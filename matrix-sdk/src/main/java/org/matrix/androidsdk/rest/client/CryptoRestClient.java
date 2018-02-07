/* 
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.androidsdk.rest.client;

import android.text.TextUtils;

import org.matrix.androidsdk.rest.model.crypto.KeyChangesResponse;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.api.CryptoApi;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.pid.DeleteDeviceParams;
import org.matrix.androidsdk.rest.model.sync.DevicesListResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysClaimResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import retrofit.client.Response;

public class CryptoRestClient extends RestClient<CryptoApi> {

    private static final String LOG_TAG = CryptoRestClient.class.getSimpleName();

    /**
     * {@inheritDoc}
     */
    public CryptoRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, CryptoApi.class, URI_API_PREFIX_PATH_UNSTABLE, false, false);
    }

    /**
     * Upload device and/or one-time keys.
     *
     * @param deviceKeys  the device keys to send.
     * @param oneTimeKeys the one-time keys to send.
     * @param deviceId    he explicit device_id to use for upload (default is to use the same as that used during auth).
     * @param callback    the asynchronous callback
     */
    public void uploadKeys(final Map<String, Object> deviceKeys, final Map<String, Object> oneTimeKeys, final String deviceId, final ApiCallback<KeysUploadResponse> callback) {
        final String description = "uploadKeys";

        String encodedDeviceId = JsonUtils.convertToUTF8(deviceId);
        HashMap<String, Object> params = new HashMap<>();

        if (null != deviceKeys) {
            params.put("device_keys", deviceKeys);
        }

        if (null != oneTimeKeys) {
            params.put("one_time_keys", oneTimeKeys);
        }

        try {
            if (!TextUtils.isEmpty(encodedDeviceId)) {
                mApi.uploadKeys(encodedDeviceId, params, new RestAdapterCallback<KeysUploadResponse>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        uploadKeys(deviceKeys, oneTimeKeys, deviceId, callback);
                    }
                }));
            } else {
                mApi.uploadKeys(params, new RestAdapterCallback<KeysUploadResponse>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        uploadKeys(deviceKeys, oneTimeKeys, deviceId, callback);
                    }
                }));
            }
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Download device keys.
     *
     * @param userIds  list of users to get keys for.
     * @param token    the up-to token
     * @param callback the asynchronous callback
     */
    public void downloadKeysForUsers(final List<String> userIds, final String token, final ApiCallback<KeysQueryResponse> callback) {
        final String description = "downloadKeysForUsers";

        HashMap<String, Map<String, Object>> downloadQuery = new HashMap<>();

        if (null != userIds) {
            for (String userId : userIds) {
                downloadQuery.put(userId, new HashMap<String, Object>());
            }
        }

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("device_keys", downloadQuery);

        if (!TextUtils.isEmpty(token)) {
            parameters.put("token", token);
        }

        try {
            mApi.downloadKeysForUsers(parameters, new RestAdapterCallback<KeysQueryResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    downloadKeysForUsers(userIds, token, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Claim one-time keys.
     *
     * @param usersDevicesKeyTypesMap a list of users, devices and key types to retrieve keys for.
     * @param callback                the asynchronous callback
     */
    public void claimOneTimeKeysForUsersDevices(final MXUsersDevicesMap<String> usersDevicesKeyTypesMap, final ApiCallback<MXUsersDevicesMap<MXKey>> callback) {
        final String description = "claimOneTimeKeysForUsersDevices";

        HashMap<String, Object> params = new HashMap<>();
        params.put("one_time_keys", usersDevicesKeyTypesMap.getMap());

        try {
            mApi.claimOneTimeKeysForUsersDevices(params, new RestAdapterCallback<KeysClaimResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    claimOneTimeKeysForUsersDevices(usersDevicesKeyTypesMap, callback);
                }
            }) {
                @Override
                public void success(KeysClaimResponse keysClaimResponse, Response response) {
                    onEventSent();

                    HashMap<String, Map<String, MXKey>> map = new HashMap();

                    if (null != keysClaimResponse.oneTimeKeys) {
                        for (String userId : keysClaimResponse.oneTimeKeys.keySet()) {
                            Map<String, Map<String, Map<String, Object>>> mapByUserId = keysClaimResponse.oneTimeKeys.get(userId);

                            HashMap<String, MXKey> keysMap = new HashMap<>();

                            for (String deviceId : mapByUserId.keySet()) {
                                try {
                                    keysMap.put(deviceId, new MXKey(mapByUserId.get(deviceId)));
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "## claimOneTimeKeysForUsersDevices : fail to create a MXKey " + e.getMessage());
                                }
                            }

                            if (keysMap.size() != 0) {
                                map.put(userId, keysMap);
                            }
                        }
                    }

                    callback.onSuccess(new MXUsersDevicesMap<>(map));
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Send an event to a specific list of devices
     *
     * @param eventType  the type of event to send
     * @param contentMap content to send. Map from user_id to device_id to content dictionary.
     * @param callback   the asynchronous callback.
     */
    public void sendToDevice(final String eventType, final MXUsersDevicesMap<Map<String, Object>> contentMap, final ApiCallback<Void> callback) {
        sendToDevice(eventType, contentMap, (new Random()).nextInt(Integer.MAX_VALUE) + "", callback);
    }

    /**
     * Send an event to a specific list of devices
     *
     * @param eventType     the type of event to send
     * @param contentMap    content to send. Map from user_id to device_id to content dictionary.
     * @param transactionId the transactionId
     * @param callback      the asynchronous callback.
     */
    public void sendToDevice(final String eventType, final MXUsersDevicesMap<Map<String, Object>> contentMap, final String transactionId, final ApiCallback<Void> callback) {
        final String description = "sendToDevice " + eventType;

        HashMap<String, Object> content = new HashMap<>();
        content.put("messages", contentMap.getMap());

        try {
            mApi.sendToDevice(eventType, transactionId, content, new RestAdapterCallback<Void>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    sendToDevice(eventType, contentMap, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Retrieves the devices informaty
     *
     * @param callback the asynchronous callback.
     */
    public void getDevices(final ApiCallback<DevicesListResponse> callback) {
        final String description = "getDevicesListInfo";

        try {
            mApi.getDevices(new RestAdapterCallback<DevicesListResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getDevices(callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Delete a device.
     *
     * @param deviceId the device id
     * @param params   the deletion parameters
     * @param callback the asynchronous callback
     */
    public void deleteDevice(final String deviceId, final DeleteDeviceParams params, final ApiCallback<Void> callback) {
        final String description = "deleteDevice";

        try {
            mApi.deleteDevice(deviceId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    deleteDevice(deviceId, params, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Set a device name.
     *
     * @param deviceId   the device id
     * @param deviceName the device name
     * @param callback   the asynchronous callback
     */
    public void setDeviceName(final String deviceId, final String deviceName, final ApiCallback<Void> callback) {
        final String description = "setDeviceName";

        HashMap<String, String> params = new HashMap<>();
        params.put("display_name", TextUtils.isEmpty(deviceName) ? "" : deviceName);

        try {
            mApi.updateDeviceInfo(deviceId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    setDeviceName(deviceId, deviceName, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Get the update devices list from two sync token.
     *
     * @param from     the start token.
     * @param to       the up-to token.
     * @param callback the asynchronous callback
     */
    public void getKeyChanges(final String from, final String to, final ApiCallback<KeyChangesResponse> callback) {
        final String description = "getKeyChanges";

        try {
            mApi.getKeyChanges(from, to, new RestAdapterCallback<KeyChangesResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getKeyChanges(from, to, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }
}
