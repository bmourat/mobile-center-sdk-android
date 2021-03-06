package com.microsoft.azure.mobile.distribute;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;

import com.microsoft.azure.mobile.channel.Channel;
import com.microsoft.azure.mobile.http.HttpClient;
import com.microsoft.azure.mobile.http.HttpClientNetworkStateHandler;
import com.microsoft.azure.mobile.http.ServiceCall;
import com.microsoft.azure.mobile.http.ServiceCallback;
import com.microsoft.azure.mobile.test.TestUtils;
import com.microsoft.azure.mobile.utils.AsyncTaskUtils;

import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static com.microsoft.azure.mobile.distribute.DistributeConstants.DOWNLOAD_STATE_AVAILABLE;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.DOWNLOAD_STATE_COMPLETED;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.INVALID_RELEASE_IDENTIFIER;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.PREFERENCE_KEY_DOWNLOAD_STATE;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.PREFERENCE_KEY_IGNORED_RELEASE_ID;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.PREFERENCE_KEY_RELEASE_DETAILS;
import static com.microsoft.azure.mobile.distribute.DistributeConstants.PREFERENCE_KEY_UPDATE_TOKEN;
import static com.microsoft.azure.mobile.utils.storage.StorageHelper.PreferencesStorage;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.whenNew;

public class DistributeBeforeDownloadTest extends AbstractDistributeTest {

    @Test
    public void moreRecentWithIncompatibleMinApiLevel() throws Exception {

        /* Mock we already have token. */
        TestUtils.setInternalState(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.JELLY_BEAN_MR2);
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        HashMap<String, String> headers = new HashMap<>();
        headers.put(DistributeConstants.HEADER_API_TOKEN, "some token");
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(releaseDetails.getMinApiLevel()).thenReturn(Build.VERSION_CODES.KITKAT);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Verify on incompatible version we complete workflow. */
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
        verify(mDialogBuilder, never()).create();
        verify(mDialog, never()).show();

        /* After that if we resume app nothing happens. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
    }

    @Test
    public void olderVersionCode() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        HashMap<String, String> headers = new HashMap<>();
        headers.put(DistributeConstants.HEADER_API_TOKEN, "some token");
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(5);
        when(releaseDetails.getMinApiLevel()).thenReturn(Build.VERSION_CODES.M);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);
        TestUtils.setInternalState(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.N_MR1);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Verify on failure we complete workflow. */
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
        verify(mDialogBuilder, never()).create();
        verify(mDialog, never()).show();

        /* After that if we resume app nothing happens. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Verify release hash was not even considered. */
        verify(releaseDetails, never()).getReleaseHash();
    }

    @Test
    public void sameVersionCodeSameHash() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        HashMap<String, String> headers = new HashMap<>();
        headers.put(DistributeConstants.HEADER_API_TOKEN, "some token");
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(6);
        when(releaseDetails.getReleaseHash()).thenReturn(TEST_HASH);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Verify on failure we complete workflow. */
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
        verify(mDialogBuilder, never()).create();
        verify(mDialog, never()).show();

        /* After that if we resume app nothing happens. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
    }

    @Test
    public void moreRecentVersionCodeWithoutReleaseNotesDialog() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        HashMap<String, String> headers = new HashMap<>();
        headers.put(DistributeConstants.HEADER_API_TOKEN, "some token");
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);
        when(InstallerUtils.isUnknownSourcesEnabled(any(Context.class))).thenReturn(true);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Verify dialog. */
        verify(mDialogBuilder).setTitle(R.string.mobile_center_distribute_update_dialog_title);
        verify(mDialogBuilder).setMessage(R.string.mobile_center_distribute_update_dialog_message);
        verify(mDialogBuilder, never()).setMessage(any(CharSequence.class));
        verify(mDialogBuilder, never()).setCancelable(false);
        verify(mDialogBuilder).create();
        verify(mDialog).show();

        /* After that if we resume app we refresh dialog. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* No more http call. */
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* But dialog refreshed. */
        InOrder order = inOrder(mDialog);
        order.verify(mDialog).hide();
        order.verify(mDialog).show();
        order.verifyNoMoreInteractions();
        verify(mDialog, times(2)).show();
        verify(mDialogBuilder, times(2)).create();

        /* Disable does not hide the dialog. */
        Distribute.setEnabled(false);

        /* We already called hide once, make sure its not called a second time. */
        verify(mDialog).hide();

        /* Also no toast if we don't click on actionable button. */
        verify(mToast, never()).show();
    }

    @Test
    public void sameVersionDifferentHashWithReleaseNotesDialog() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        HashMap<String, String> headers = new HashMap<>();
        headers.put(DistributeConstants.HEADER_API_TOKEN, "some token");
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(6);
        when(releaseDetails.getReleaseHash()).thenReturn("9f52199c986d9210842824df695900e1656180946212bd5e8978501a5b732e60");
        when(releaseDetails.getReleaseNotes()).thenReturn("mock");
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Verify dialog. */
        verify(mDialogBuilder).setTitle(R.string.mobile_center_distribute_update_dialog_title);
        verify(mDialogBuilder).setMessage("mock");
        verify(mDialogBuilder, never()).setCancelable(false);
        verify(mDialogBuilder).create();
        verify(mDialog).show();
    }

    @Test
    public void dialogActivityStateChanges() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        final Semaphore beforeSemaphore = new Semaphore(0);
        final Semaphore afterSemaphore = new Semaphore(0);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(final InvocationOnMock invocation) throws Throwable {
                new Thread() {

                    @Override
                    public void run() {
                        beforeSemaphore.acquireUninterruptibly();
                        ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                        afterSemaphore.release();
                    }
                }.start();
                return mock(ServiceCall.class);
            }
        });
        HashMap<String, String> headers = new HashMap<>();
        headers.put(DistributeConstants.HEADER_API_TOKEN, "some token");
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(releaseDetails.getReleaseNotes()).thenReturn("mock");
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Activity activity = mock(Activity.class);
        Distribute.getInstance().onActivityResumed(activity);
        Distribute.getInstance().onActivityPaused(activity);
        verify(httpClient).callAsync(anyString(), anyString(), eq(headers), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Release call in background. */
        beforeSemaphore.release();
        afterSemaphore.acquireUninterruptibly();

        /* Verify dialog not shown. */
        verify(mDialogBuilder, never()).create();
        verify(mDialog, never()).show();

        /* Go foreground. */
        Distribute.getInstance().onActivityResumed(activity);

        /* Verify dialog now shown. */
        verify(mDialogBuilder).create();
        verify(mDialog).show();

        /* Pause/resume should not alter dialog. */
        Distribute.getInstance().onActivityPaused(activity);
        Distribute.getInstance().onActivityResumed(activity);

        /* Only once check, and no hiding. */
        verify(mDialogBuilder).create();
        verify(mDialog).show();
        verify(mDialog, never()).hide();

        /* Cover activity. Dialog must be replaced. */
        Distribute.getInstance().onActivityPaused(activity);
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialogBuilder, times(2)).create();
        verify(mDialog, times(2)).show();
        verify(mDialog).hide();
    }

    @Test
    public void cancelDialog() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog. */
        ArgumentCaptor<DialogInterface.OnCancelListener> cancelListener = ArgumentCaptor.forClass(DialogInterface.OnCancelListener.class);
        verify(mDialogBuilder).setOnCancelListener(cancelListener.capture());
        verify(mDialog).show();

        /* Cancel it. */
        cancelListener.getValue().onCancel(mDialog);
        when(mDialog.isShowing()).thenReturn(false);

        /* Verify. */
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);

        /* Verify no more calls, e.g. happened only once. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog).show();
        verify(httpClient).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Restart should check release and show dialog again. */
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog, times(2)).show();
        verify(httpClient, times(2)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
    }

    @Test
    public void postponeDialog() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog. */
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder).setNeutralButton(eq(R.string.mobile_center_distribute_update_dialog_postpone), clickListener.capture());
        verify(mDialog).show();

        /* Postpone it. */
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_NEUTRAL);
        when(mDialog.isShowing()).thenReturn(false);

        /* Verify. */
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_RELEASE_DETAILS);

        /* Verify no more calls, e.g. happened only once. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog).show();
        verify(httpClient).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Restart should check release and show dialog again. */
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog, times(2)).show();
        verify(httpClient, times(2)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
    }

    @Test
    public void ignoreDialog() throws Exception {

        /* Mock ignore storage calls. */
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                when(PreferencesStorage.getInt(invocation.getArguments()[0].toString(), INVALID_RELEASE_IDENTIFIER)).thenReturn((int) invocation.getArguments()[1]);
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.putInt(eq(PREFERENCE_KEY_IGNORED_RELEASE_ID), anyInt());
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                when(PreferencesStorage.getInt(invocation.getArguments()[0].toString(), INVALID_RELEASE_IDENTIFIER)).thenReturn(INVALID_RELEASE_IDENTIFIER);
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.remove(PREFERENCE_KEY_IGNORED_RELEASE_ID);

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog. */
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder).setNegativeButton(eq(R.string.mobile_center_distribute_update_dialog_ignore), clickListener.capture());
        verify(mDialog).show();

        /* Ignore it. */
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_NEGATIVE);
        when(mDialog.isShowing()).thenReturn(false);

        /* Verify. */
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);

        /* Verify no more calls, e.g. happened only once. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog).show();
        verify(httpClient).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Restart app to check ignore. */
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify second http call was made but dialog was skipped (e.g. shown only once). */
        verify(httpClient, times(2)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        verify(mDialog).show();
        verifyStatic(times(2));
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);

        /* Disable: it will prompt again as we clear storage. */
        Distribute.setEnabled(false);
        Distribute.setEnabled(true);
        verify(httpClient, times(3)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        verify(mDialog, times(2)).show();
    }

    @Test
    public void disableBeforeCancelDialog() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog. */
        ArgumentCaptor<DialogInterface.OnCancelListener> cancelListener = ArgumentCaptor.forClass(DialogInterface.OnCancelListener.class);
        verify(mDialogBuilder).setOnCancelListener(cancelListener.capture());
        verify(mDialog).show();

        /* Disable. */
        Distribute.setEnabled(false);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);

        /* Cancel it. */
        cancelListener.getValue().onCancel(mDialog);
        when(mDialog.isShowing()).thenReturn(false);

        /* Verify no more calls, e.g. happened only once. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog).show();
        verify(httpClient).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
    }

    @Test
    public void disableBeforeIgnoreDialog() throws Exception {

        /* Mock ignore storage calls. */
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                when(PreferencesStorage.getInt(invocation.getArguments()[0].toString(), INVALID_RELEASE_IDENTIFIER)).thenReturn((int) invocation.getArguments()[1]);
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.putInt(eq(PREFERENCE_KEY_IGNORED_RELEASE_ID), anyInt());
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                when(PreferencesStorage.getInt(invocation.getArguments()[0].toString(), INVALID_RELEASE_IDENTIFIER)).thenReturn(INVALID_RELEASE_IDENTIFIER);
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.remove(PREFERENCE_KEY_IGNORED_RELEASE_ID);

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);
        when(InstallerUtils.isUnknownSourcesEnabled(any(Context.class))).thenReturn(true);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog. */
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder).setNegativeButton(eq(R.string.mobile_center_distribute_update_dialog_ignore), clickListener.capture());
        verify(mDialog).show();

        /* Disable. */
        Distribute.setEnabled(false);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);

        /* Ignore it. */
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_NEGATIVE);
        when(mDialog.isShowing()).thenReturn(false);

        /* Since we were disabled, no action but toast to explain what happened. */
        verify(mToast).show();

        /* Verify no more calls, e.g. happened only once. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog).show();
        verify(httpClient).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_IGNORED_RELEASE_ID);
        verifyStatic(never());
        PreferencesStorage.putInt(eq(PREFERENCE_KEY_IGNORED_RELEASE_ID), anyInt());
    }

    @Test
    @PrepareForTest(AsyncTaskUtils.class)
    public void disableBeforeDownload() throws Exception {

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                ((ServiceCallback) invocation.getArguments()[4]).onCallSucceeded("mock");
                return mock(ServiceCall.class);
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);
        mockStatic(AsyncTaskUtils.class);
        when(InstallerUtils.isUnknownSourcesEnabled(any(Context.class))).thenReturn(true);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog. */
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), clickListener.capture());
        verify(mDialog).show();

        /* Disable. */
        Distribute.setEnabled(false);
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);

        /* Click on download. */
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        when(mDialog.isShowing()).thenReturn(false);

        /* Since we were disabled, no action but toast to explain what happened. */
        verify(mToast).show();

        /* Verify no more calls, e.g. happened only once. */
        Distribute.getInstance().onActivityPaused(mock(Activity.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialog).show();
        verify(httpClient).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        verifyStatic();
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);

        /* Verify no download scheduled. */
        verifyStatic(never());
        AsyncTaskUtils.execute(anyString(), any(DownloadTask.class), Mockito.<Void>anyVararg());
    }

    @Test
    public void mandatoryUpdateDialogAndCacheTests() throws Exception {

        /* Mock some storage calls. */
        mockSomeStorage();

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        final AtomicReference<ServiceCallback> serviceCallbackRef = new AtomicReference<>();
        final ServiceCall serviceCall = mock(ServiceCall.class);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                Object serviceCallback = invocation.getArguments()[4];
                if (serviceCallback instanceof ServiceCallback) {
                    serviceCallbackRef.set((ServiceCallback) serviceCallback);
                }
                return serviceCall;
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(releaseDetails.isMandatoryUpdate()).thenReturn(true);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        assertNotNull(serviceCallbackRef.get());
        serviceCallbackRef.get().onCallSucceeded("mock");
        serviceCallbackRef.set(null);

        /* Verify release notes persisted. */
        verifyStatic();
        PreferencesStorage.putString(PREFERENCE_KEY_RELEASE_DETAILS, "mock");
        verifyStatic();
        PreferencesStorage.putInt(PREFERENCE_KEY_DOWNLOAD_STATE, DOWNLOAD_STATE_AVAILABLE);

        /* Verify dialog. */
        verify(mDialogBuilder, never()).setNeutralButton(anyString(), any(DialogInterface.OnClickListener.class));
        verify(mDialogBuilder, never()).setNegativeButton(anyString(), any(DialogInterface.OnClickListener.class));
        verify(mDialogBuilder).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), any(DialogInterface.OnClickListener.class));
        verify(mDialogBuilder).setCancelable(false);

        /* Verify dialog restored offline even if process restarts. */
        when(mNetworkStateHelper.isNetworkConnected()).thenReturn(false);
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialogBuilder, times(2)).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), any(DialogInterface.OnClickListener.class));
        verify(mDialogBuilder, never()).setOnCancelListener(any(DialogInterface.OnCancelListener.class));
        verify(mDialogBuilder, times(2)).setCancelable(false);
        verify(httpClient, times(2)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        assertNotNull(serviceCallbackRef.get());

        /* Simulate network back and get same release again, should do nothing particular. */
        when(mNetworkStateHelper.isNetworkConnected()).thenReturn(true);
        serviceCallbackRef.get().onCallSucceeded("mock");

        /* Check we didn't change state, e.g. happened only once. */
        verifyStatic();
        PreferencesStorage.putInt(PREFERENCE_KEY_DOWNLOAD_STATE, DOWNLOAD_STATE_AVAILABLE);

        /* Restart and this time we will detect a more recent optional release. */
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify call is made and that we restored again mandatory update dialog in the mean time. */
        verify(httpClient, times(3)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        verify(mDialogBuilder, times(3)).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), any(DialogInterface.OnClickListener.class));
        verify(mDialogBuilder, never()).setOnCancelListener(any(DialogInterface.OnCancelListener.class));
        verify(mDialogBuilder, times(3)).setCancelable(false);

        /* Then detect new release in background. */
        releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(5);
        when(releaseDetails.getVersion()).thenReturn(8);
        when(releaseDetails.isMandatoryUpdate()).thenReturn(false);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);
        serviceCallbackRef.get().onCallSucceeded("mock");

        /* Check state updated again when we detect it. */
        verifyStatic(times(2));
        PreferencesStorage.putInt(PREFERENCE_KEY_DOWNLOAD_STATE, DOWNLOAD_STATE_AVAILABLE);

        /* Restart SDK, even offline, should show optional dialog. */
        when(mNetworkStateHelper.isNetworkConnected()).thenReturn(false);
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));
        verify(mDialogBuilder, times(4)).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), any(DialogInterface.OnClickListener.class));
        verify(mDialogBuilder).setOnCancelListener(any(DialogInterface.OnCancelListener.class));
        verify(mDialogBuilder, times(3)).setCancelable(false);

        /* And still check again for further update. */
        verify(httpClient, times(4)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));

        /* Unblock call with network up. */
        when(mNetworkStateHelper.isNetworkConnected()).thenReturn(true);
        serviceCallbackRef.get().onCallSucceeded("mock");

        /* If we restart SDK online, its an optional update so dialog will not be restored until new call made. */
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog behavior happened only once. */
        verify(mDialogBuilder).setOnCancelListener(any(DialogInterface.OnCancelListener.class));

        /* Dialog shown only after new call made in that scenario. */
        serviceCallbackRef.get().onCallSucceeded("mock");
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder, times(5)).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), clickListener.capture());
        verify(mDialogBuilder, times(2)).setOnCancelListener(any(DialogInterface.OnCancelListener.class));

        /* If we finally click on download, no call cancel since already successful. */
        when(InstallerUtils.isUnknownSourcesEnabled(mContext)).thenReturn(true);
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        verify(serviceCall, never()).cancel();
    }

    @Test
    public void cancelGetReleaseCallIfDownloadingCachedDialogAfterRestart() throws Exception {

        /* Mock some storage calls. */
        mockSomeStorage();

        /* Mock we already have token. */
        when(PreferencesStorage.getString(PREFERENCE_KEY_UPDATE_TOKEN)).thenReturn("some token");
        HttpClientNetworkStateHandler httpClient = mock(HttpClientNetworkStateHandler.class);
        whenNew(HttpClientNetworkStateHandler.class).withAnyArguments().thenReturn(httpClient);
        final AtomicReference<ServiceCallback> serviceCallbackRef = new AtomicReference<>();
        final ServiceCall serviceCall = mock(ServiceCall.class);
        when(httpClient.callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class))).thenAnswer(new Answer<ServiceCall>() {

            @Override
            public ServiceCall answer(InvocationOnMock invocation) throws Throwable {
                Object serviceCallback = invocation.getArguments()[4];
                if (serviceCallback instanceof ServiceCallback) {
                    serviceCallbackRef.set((ServiceCallback) serviceCallback);
                }
                return serviceCall;
            }
        });
        ReleaseDetails releaseDetails = mock(ReleaseDetails.class);
        when(releaseDetails.getId()).thenReturn(4);
        when(releaseDetails.getVersion()).thenReturn(7);
        when(releaseDetails.isMandatoryUpdate()).thenReturn(false);
        when(ReleaseDetails.parse(anyString())).thenReturn(releaseDetails);

        /* Trigger call. */
        Distribute.getInstance().onStarted(mContext, "a", mock(Channel.class));
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog. */
        serviceCallbackRef.get().onCallSucceeded("mock");
        verify(mDialogBuilder).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), any(DialogInterface.OnClickListener.class));

        /* Restart offline. */
        when(mNetworkStateHelper.isNetworkConnected()).thenReturn(false);
        restartProcessAndSdk();
        Distribute.getInstance().onActivityResumed(mock(Activity.class));

        /* Verify dialog restored and call scheduled. */
        verify(httpClient, times(2)).callAsync(anyString(), anyString(), anyMapOf(String.class, String.class), any(HttpClient.CallTemplate.class), any(ServiceCallback.class));
        ArgumentCaptor<DialogInterface.OnClickListener> clickListener = ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mDialogBuilder, times(2)).setPositiveButton(eq(R.string.mobile_center_distribute_update_dialog_download), clickListener.capture());

        /* We are offline and call is scheduled, clicking download must cancel pending call. */
        when(InstallerUtils.isUnknownSourcesEnabled(mContext)).thenReturn(true);
        clickListener.getValue().onClick(mDialog, DialogInterface.BUTTON_POSITIVE);
        verify(serviceCall).cancel();
    }

    /** Mock some storage calls. */
    private void mockSomeStorage() {
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                PowerMockito.when(PreferencesStorage.getInt(invocation.getArguments()[0].toString(), DOWNLOAD_STATE_COMPLETED)).thenReturn((Integer) invocation.getArguments()[1]);
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.putInt(eq(PREFERENCE_KEY_DOWNLOAD_STATE), anyInt());
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                PowerMockito.when(PreferencesStorage.getInt(invocation.getArguments()[0].toString(), DOWNLOAD_STATE_COMPLETED)).thenReturn(DOWNLOAD_STATE_COMPLETED);
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.remove(PREFERENCE_KEY_DOWNLOAD_STATE);
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                PowerMockito.when(PreferencesStorage.getString(invocation.getArguments()[0].toString())).thenReturn(invocation.getArguments()[1].toString());
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.putString(eq(PREFERENCE_KEY_RELEASE_DETAILS), anyString());
        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                PowerMockito.when(PreferencesStorage.getString(invocation.getArguments()[0].toString())).thenReturn(null);
                return null;
            }
        }).when(PreferencesStorage.class);
        PreferencesStorage.remove(PREFERENCE_KEY_RELEASE_DETAILS);
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.setInternalState(Build.VERSION.class, "SDK_INT", 0);
    }
}
