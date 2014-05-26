/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.provider.TvContract;

/**
 * A convenience class to create and insert channel entries into the database.
 */
public final class Channel {
    public static final long INVALID_ID = -1;

    /** ID of this channel. Matches to BaseColumns._ID. */
    private long mId;

    private String mServiceName;
    private int mType;
    private int mOriginalNetworkId;
    private int mTransportStreamId;
    private String mDisplayNumber;
    private String mDisplayName;
    private String mDescription;
    private boolean mIsBrowsable;
    private byte[] mData;

    public static Channel fromCursor(Cursor cursor) {
        Channel channel = new Channel();
        int index = cursor.getColumnIndex(TvContract.Channels._ID);
        if (index >= 0) {
            channel.mId = cursor.getLong(index);
        } else {
            channel.mId = INVALID_ID;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_SERVICE_NAME);
        if (index >= 0) {
            channel.mServiceName = cursor.getString(index);
        } else {
            channel.mServiceName = "serviceName";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TYPE);
        if (index >= 0) {
            channel.mType = cursor.getInt(index);
        } else {
            channel.mType = 0;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID);
        if (index >= 0) {
            channel.mTransportStreamId = cursor.getInt(index);
        } else {
            channel.mTransportStreamId = 0;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID);
        if (index >= 0) {
            channel.mOriginalNetworkId = cursor.getInt(index);
        } else {
            channel.mOriginalNetworkId = 0;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        if (index >= 0) {
            channel.mDisplayNumber = cursor.getString(index);
        } else {
            channel.mDisplayNumber = "0";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
        if (index >= 0) {
            channel.mDisplayName = cursor.getString(index);
        } else {
            channel.mDisplayName = "name";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DESCRIPTION);
        if (index >= 0) {
            channel.mDescription = cursor.getString(index);
        } else {
            channel.mDescription = "description";
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_BROWSABLE);
        if (index >= 0) {
            channel.mIsBrowsable = cursor.getInt(index) == 1;
        } else {
            channel.mIsBrowsable = true;
        }

        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DATA);
        if (index >= 0) {
            channel.mData = cursor.getBlob(index);
        } else {
            channel.mData = null;
        }
        return channel;
    }

    private Channel() {
        // Do nothing.
    }

    public long getId() {
        return mId;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public int getType() {
        return mType;
    }

    public int getOriginalNetworkId() {
        return mOriginalNetworkId;
    }

    public int getTransportStreamId() {
        return mTransportStreamId;
    }

    public String getDisplayNumber() {
        return mDisplayNumber;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDescription() {
        return mDescription;
    }

    public boolean isBrowsable() {
        return mIsBrowsable;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public void setBrowsable(boolean browsable) {
        mIsBrowsable = browsable;
    }

    public byte[] getData() {
        return mData;
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(TvContract.Channels.COLUMN_SERVICE_NAME, mServiceName);
        values.put(TvContract.Channels.COLUMN_TYPE, mType);
        values.put(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID, mTransportStreamId);
        values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, mDisplayNumber);
        values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, mDisplayName);
        values.put(TvContract.Channels.COLUMN_DESCRIPTION, mDescription);
        values.put(TvContract.Channels.COLUMN_BROWSABLE, mIsBrowsable ? 1 : 0);
        values.put(TvContract.Channels.COLUMN_DATA, mData);
        return values;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Channel{")
                .append("id=").append(mId)
                .append(", serviceName=").append(mServiceName)
                .append(", type=").append(mType)
                .append(", originalNetworkId=").append(mOriginalNetworkId)
                .append(", transportStreamId=").append(mTransportStreamId)
                .append(", displayNumber=").append(mDisplayNumber)
                .append(", displayName=").append(mDisplayName)
                .append(", description=").append(mDescription)
                .append(", browsable=").append(mIsBrowsable)
                .append(", data=").append(mData)
                .append("}")
                .toString();
    }

    public void copyFrom(Channel other) {
        if (this == other) {
            return;
        }
        mId = other.mId;
        mServiceName = other.mServiceName;
        mType = other.mType;
        mTransportStreamId = other.mTransportStreamId;
        mOriginalNetworkId = other.mOriginalNetworkId;
        mDisplayNumber = other.mDisplayNumber;
        mDisplayName = other.mDisplayName;
        mDescription = other.mDescription;
        mIsBrowsable = other.mIsBrowsable;
        mData = other.mData;
    }

    public static final class Builder {
        private final Channel mChannel;

        public Builder() {
            mChannel = new Channel();
            // Fill initial data.
            mChannel.mId = INVALID_ID;
            mChannel.mServiceName = "serviceName";
            mChannel.mType = 0;
            mChannel.mTransportStreamId = 0;
            mChannel.mOriginalNetworkId = 0;
            mChannel.mDisplayNumber = "0";
            mChannel.mDisplayName = "name";
            mChannel.mDescription = "description";
            mChannel.mIsBrowsable = true;
            mChannel.mData = null;
        }

        public Builder(Channel other) {
            mChannel = new Channel();
            mChannel.copyFrom(other);
        }

        public Builder setId(long id) {
            mChannel.mId = id;
            return this;
        }

        public Builder setServiceName(String serviceName) {
            mChannel.mServiceName = serviceName;
            return this;
        }

        public Builder setType(int type) {
            mChannel.mType = type;
            return this;
        }

        public Builder setTransportStreamId(int transportStreamId) {
            mChannel.mTransportStreamId = transportStreamId;
            return this;
        }

        public Builder setOriginalNetworkId(int originalNetworkId) {
            mChannel.mOriginalNetworkId = originalNetworkId;
            return this;
        }

        public Builder setDisplayNumber(String displayNumber) {
            mChannel.mDisplayNumber = displayNumber;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            mChannel.mDisplayName = displayName;
            return this;
        }

        public Builder setDescription(String description) {
            mChannel.mDescription = description;
            return this;
        }

        public Builder setBrowsable(boolean browsable) {
            mChannel.mIsBrowsable = browsable;
            return this;
        }

        public Builder setData(byte[] data) {
            mChannel.mData = data;
            return this;
        }

        public Channel build() {
            return mChannel;
        }
    }
}