/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.tuner.exoplayer2.buffer;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * {@link SampleChunk} stores samples into file and makes them available for read. Stored file = {
 * Header, Sample } * N Header = sample size : int, sample flag : int, sample PTS in micro second :
 * long
 */
public class SampleChunk {
    private static final String TAG = "SampleChunk";
    private static final boolean DEBUG = false;

    // The flag values should not be changed.
    /**
     * This indicates that the (encoded) buffer marked as such contains
     * the data for a key frame.
     */
    private static final int BUFFER_FLAG_KEY_FRAME = 1;
    /** Indicates that a buffer should be decoded but not rendered. */
    private static final int BUFFER_FLAG_DECODE_ONLY = 1 << 31; // 0x80000000
    /** Indicates that a buffer is (at least partially) encrypted. */
    private static final int BUFFER_FLAG_ENCRYPTED = 1 << 30; // 0x40000000

    private final long mCreatedTimeMs;
    private final long mStartPositionUs;
    private SampleChunk mNextChunk;

    // Header = sample size : int, sample flag : int, sample PTS in micro second : long
    private static final int SAMPLE_HEADER_LENGTH = 16;

    private final File mFile;
    private final ChunkCallback mChunkCallback;
    private final InputBufferPool mInputBufferPool;
    private RandomAccessFile mAccessFile;
    private long mWriteOffset;
    private boolean mWriteFinished;
    private boolean mIsReading;
    private boolean mIsWriting;

    /** A callback for chunks being committed to permanent storage. */
    public abstract static class ChunkCallback {

        /**
         * Notifies when writing a SampleChunk is completed.
         *
         * @param chunk SampleChunk which is written completely
         */
        public void onChunkWrite(SampleChunk chunk) {}

        /**
         * Notifies when a SampleChunk is deleted.
         *
         * @param chunk SampleChunk which is deleted from storage
         */
        public void onChunkDelete(SampleChunk chunk) {}
    }

    /** A class for SampleChunk creation. */
    public static class SampleChunkCreator {

        /**
         * Returns a newly created SampleChunk to read & write samples.
         *
         * @param inputBufferPool sample allocator
         * @param file filename which will be created newly
         * @param startPositionUs the start position of the earliest sample to be stored
         * @param chunkCallback for total storage usage change notification
         */
        @VisibleForTesting
        SampleChunk createSampleChunk(
                InputBufferPool inputBufferPool,
                File file,
                long startPositionUs,
                ChunkCallback chunkCallback) {
            return new SampleChunk(
                    inputBufferPool,
                    file,
                    startPositionUs,
                    System.currentTimeMillis(),
                    chunkCallback);
        }

        /**
         * Returns a newly created SampleChunk which is backed by an existing file. Created
         * SampleChunk is read-only.
         *
         * @param inputBufferPool sample allocator
         * @param bufferDir the directory where the file to read is located
         * @param filename the filename which will be read afterwards
         * @param startPositionUs the start position of the earliest sample in the file
         * @param chunkCallback for total storage usage change notification
         * @param prev the previous SampleChunk just before the newly created SampleChunk
         */
        SampleChunk loadSampleChunkFromFile(
                InputBufferPool inputBufferPool,
                File bufferDir,
                String filename,
                long startPositionUs,
                ChunkCallback chunkCallback,
                SampleChunk prev) {
            File file = new File(bufferDir, filename);
            SampleChunk chunk =
                    new SampleChunk(inputBufferPool, file, startPositionUs, chunkCallback);
            if (prev != null) {
                prev.mNextChunk = chunk;
            }
            return chunk;
        }
    }

    /**
     * Handles I/O for SampleChunk. Maintains current SampleChunk and the current offset for next
     * I/O operation.
     */
    @VisibleForTesting
    static class IoState {
        private SampleChunk mChunk;
        private long mCurrentOffset;

        private boolean equals(SampleChunk chunk, long offset) {
            return chunk == mChunk && mCurrentOffset == offset;
        }

        /** Returns whether read I/O operation is finished. */
        boolean isReadFinished() {
            return mChunk == null;
        }

        /** Returns the start position of the current SampleChunk */
        long getStartPositionUs() {
            return mChunk == null ? 0 : mChunk.getStartPositionUs();
        }

        private void reset(@Nullable SampleChunk chunk) {
            mChunk = chunk;
            mCurrentOffset = 0;
        }

        private void reset(SampleChunk chunk, long offset) {
            mChunk = chunk;
            mCurrentOffset = offset;
        }

        /**
         * Prepares for read I/O operation from a new SampleChunk.
         *
         * @param chunk the new SampleChunk to read from
         * @throws IOException if an I/O error occurs.
         */
        void openRead(SampleChunk chunk, long offset) throws IOException {
            if (mChunk != null) {
                mChunk.closeRead();
            }
            chunk.openRead();
            reset(chunk, offset);
        }

        /**
         * Prepares for write I/O operation to a new SampleChunk.
         *
         * @param chunk the new SampleChunk to write samples afterwards
         * @throws IOException if an I/O error occurs.
         */
        void openWrite(SampleChunk chunk) throws IOException {
            if (mChunk != null) {
                mChunk.closeWrite(chunk);
            }
            chunk.openWrite();
            reset(chunk);
        }

        /**
         * Reads a sample if it is available.
         *
         * @return Returns a sample if it is available, null otherwise.
         * @throws IOException if an I/O error occurs.
         */
        DecoderInputBuffer read() throws IOException {
            if (mChunk != null && mChunk.isReadFinished(this)) {
                SampleChunk next = mChunk.mNextChunk;
                mChunk.closeRead();
                if (next != null) {
                    next.openRead();
                }
                reset(next);
            }
            if (mChunk != null) {
                try {
                    return mChunk.read(this);
                } catch (IllegalStateException e) {
                    // Write is finished and there is no additional buffer to read.
                    Log.w(TAG, "Tried to read sample over EOS.");
                    return null;
                }
            } else {
                return null;
            }
        }

        /**
         * Writes a sample.
         *
         * @param sample to write
         * @param nextChunk if this is {@code null} writes at the current SampleChunk, otherwise
         *     close current SampleChunk and writes at this
         * @throws IOException if an I/O error occurs.
         */
        void write(DecoderInputBuffer sample, SampleChunk nextChunk) throws IOException {
            if (mChunk == null) {
                throw new IOException("mChunk should not be null");
            }
            if (nextChunk != null) {
                if (mChunk.mNextChunk != null) {
                    throw new IllegalStateException("Requested write for wrong SampleChunk");
                }
                mChunk.closeWrite(nextChunk);
                mChunk.mChunkCallback.onChunkWrite(mChunk);
                nextChunk.openWrite();
                reset(nextChunk);
            }
            mChunk.write(sample, this);
        }

        /**
         * Finishes write I/O operation.
         *
         * @throws IOException if an I/O error occurs.
         */
        void closeWrite() throws IOException {
            if (mChunk != null) {
                mChunk.closeWrite(null);
            }
        }

        /** Returns the current SampleChunk for subsequent I/O operation. */
        SampleChunk getChunk() {
            return mChunk;
        }

        /** Returns the current offset of the current SampleChunk for subsequent I/O operation. */
        long getOffset() {
            return mCurrentOffset;
        }

        /**
         * Releases SampleChunk. the SampleChunk will not be used anymore.
         *
         * @param chunk to release
         * @param delete {@code true} when the backed file needs to be deleted, {@code false}
         *     otherwise.
         */
        static void release(SampleChunk chunk, boolean delete) {
            chunk.release(delete);
        }
    }

    @VisibleForTesting
    SampleChunk(
            InputBufferPool inputBufferPool,
            File file,
            long startPositionUs,
            long createdTimeMs,
            ChunkCallback chunkCallback) {
        mStartPositionUs = startPositionUs;
        mCreatedTimeMs = createdTimeMs;
        mInputBufferPool = inputBufferPool;
        mFile = file;
        mChunkCallback = chunkCallback;
    }

    // Constructor of SampleChunk which is backed by the given existing file.
    private SampleChunk(
            InputBufferPool inputBufferPool,
            File file,
            long startPositionUs,
            ChunkCallback chunkCallback) {
        mStartPositionUs = startPositionUs;
        mCreatedTimeMs = mStartPositionUs / 1000;
        mInputBufferPool = inputBufferPool;
        mFile = file;
        mChunkCallback = chunkCallback;
        mWriteFinished = true;
    }

    private void openRead() throws IOException {
        if (!mIsReading) {
            if (mAccessFile == null) {
                mAccessFile = new RandomAccessFile(mFile, "r");
            }
            if (mWriteFinished && mWriteOffset == 0) {
                // Lazy loading of write offset, in order not to load
                // all SampleChunk's write offset at start time of recorded playback.
                mWriteOffset = mAccessFile.length();
            }
            mIsReading = true;
        }
    }

    private void openWrite() throws IOException {
        if (mWriteFinished) {
            throw new IllegalStateException("Opened for write though write is already finished");
        }
        if (!mIsWriting) {
            if (mIsReading) {
                throw new IllegalStateException(
                        "Write is requested for " + "an already opened SampleChunk");
            }
            mAccessFile = new RandomAccessFile(mFile, "rw");
            mIsWriting = true;
        }
    }

    private void CloseAccessFileIfNeeded() throws IOException {
        if (!mIsReading && !mIsWriting) {
            try {
                if (mAccessFile != null) {
                    mAccessFile.close();
                }
            } finally {
                mAccessFile = null;
            }
        }
    }

    private void closeRead() throws IOException {
        if (mIsReading) {
            mIsReading = false;
            CloseAccessFileIfNeeded();
        }
    }

    private void closeWrite(SampleChunk nextChunk) throws IOException {
        if (mIsWriting) {
            mNextChunk = nextChunk;
            mIsWriting = false;
            mWriteFinished = true;
            CloseAccessFileIfNeeded();
        }
    }

    private boolean isReadFinished(IoState state) {
        return mWriteFinished && state.equals(this, mWriteOffset);
    }

    private DecoderInputBuffer read(IoState state) throws IOException {
        if (mAccessFile == null || state.mChunk != this) {
            throw new IllegalStateException("Requested read for wrong SampleChunk");
        }
        long offset = state.mCurrentOffset;
        if (offset >= mWriteOffset) {
            if (mWriteFinished) {
                throw new IllegalStateException("Requested read for wrong range");
            } else {
                if (offset != mWriteOffset) {
                    Log.e(TAG, "This should not happen!");
                }
                return null;
            }
        }
        mAccessFile.seek(offset);
        int size = mAccessFile.readInt();
        DecoderInputBuffer sample = mInputBufferPool.acquireSample(size);
        int flags = mAccessFile.readInt();
        flags = (isKeyFrame(flags) ? C.BUFFER_FLAG_KEY_FRAME : 0)
                | (isDecodeOnly(flags) ? C.BUFFER_FLAG_DECODE_ONLY : 0)
                | (isEncrypted(flags) ? C.BUFFER_FLAG_ENCRYPTED : 0);
        sample.setFlags(flags);
        sample.timeUs = mAccessFile.readLong();
        sample.data.clear();
        sample.data.put(
                mAccessFile
                        .getChannel()
                        .map(
                                FileChannel.MapMode.READ_ONLY,
                                offset + SAMPLE_HEADER_LENGTH,
                                size));
        offset += size + SAMPLE_HEADER_LENGTH;
        state.mCurrentOffset = offset;
        return sample;
    }

    private boolean isKeyFrame(int flag) {
        return (flag & BUFFER_FLAG_KEY_FRAME) == BUFFER_FLAG_KEY_FRAME;
    }

    private boolean isDecodeOnly(int flag) {
        return (flag & BUFFER_FLAG_DECODE_ONLY) == BUFFER_FLAG_DECODE_ONLY;
    }

    private boolean isEncrypted(int flag) {
        return (flag & BUFFER_FLAG_ENCRYPTED) == BUFFER_FLAG_ENCRYPTED;
    }

    @VisibleForTesting
    void write(DecoderInputBuffer sample, IoState state) throws IOException {
        if (mAccessFile == null || mNextChunk != null || !state.equals(this, mWriteOffset)) {
            throw new IllegalStateException("Requested write for wrong SampleChunk");
        }

        mAccessFile.seek(mWriteOffset);
        int size = sample.data.position();
        mAccessFile.writeInt(size);
        int flags = (sample.isKeyFrame() ? BUFFER_FLAG_KEY_FRAME : 0)
                | (sample.isDecodeOnly() ? BUFFER_FLAG_DECODE_ONLY : 0)
                | (sample.isEncrypted() ? BUFFER_FLAG_ENCRYPTED : 0);
        mAccessFile.writeInt(flags);
        mAccessFile.writeLong(sample.timeUs);
        sample.data.position(0).limit(size);
        mAccessFile.getChannel().position(mWriteOffset + SAMPLE_HEADER_LENGTH).write(sample.data);
        mWriteOffset += size + SAMPLE_HEADER_LENGTH;
        state.mCurrentOffset = mWriteOffset;
    }

    private void release(boolean delete) {
        mWriteFinished = true;
        mIsReading = mIsWriting = false;
        try {
            if (mAccessFile != null) {
                mAccessFile.close();
            }
        } catch (IOException e) {
            // Since the SampleChunk will not be reused, ignore exception.
        }
        if (delete) {
            mFile.delete();
            mChunkCallback.onChunkDelete(this);
        }
    }

    /** Returns the start position. */
    public long getStartPositionUs() {
        return mStartPositionUs;
    }

    /** Returns the creation time. */
    public long getCreatedTimeMs() {
        return mCreatedTimeMs;
    }

    /** Returns the current size. */
    public long getSize() {
        return mWriteOffset;
    }
}
