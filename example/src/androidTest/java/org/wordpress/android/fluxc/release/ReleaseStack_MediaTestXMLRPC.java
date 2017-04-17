package org.wordpress.android.fluxc.release;

import org.apache.commons.lang.RandomStringUtils;
import org.greenrobot.eventbus.Subscribe;
import org.wordpress.android.fluxc.TestUtils;
import org.wordpress.android.fluxc.action.MediaAction;
import org.wordpress.android.fluxc.example.BuildConfig;
import org.wordpress.android.fluxc.generated.MediaActionBuilder;
import org.wordpress.android.fluxc.model.MediaModel;
import org.wordpress.android.fluxc.network.HTTPAuthManager;
import org.wordpress.android.fluxc.network.MemorizingTrustManager;
import org.wordpress.android.fluxc.store.AccountStore;
import org.wordpress.android.fluxc.store.MediaStore;
import org.wordpress.android.fluxc.store.MediaStore.FetchMediaListPayload;
import org.wordpress.android.fluxc.store.MediaStore.MediaErrorType;
import org.wordpress.android.fluxc.store.MediaStore.OnMediaChanged;
import org.wordpress.android.fluxc.store.MediaStore.OnMediaListFetched;
import org.wordpress.android.fluxc.store.MediaStore.OnMediaUploaded;
import org.wordpress.android.fluxc.utils.MediaUtils;
import org.wordpress.android.util.AppLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class ReleaseStack_MediaTestXMLRPC extends ReleaseStack_XMLRPCBase {
    @SuppressWarnings("unused")
    @Inject AccountStore mAccountStore;
    @SuppressWarnings("unused")
    @Inject HTTPAuthManager mHTTPAuthManager;
    @SuppressWarnings("unused")
    @Inject MemorizingTrustManager mMemorizingTrustManager;
    @Inject MediaStore mMediaStore;

    private enum TestEvents {
        NONE,
        PUSHED_MEDIA,
        FETCHED_MEDIA_LIST,
        FETCHED_MEDIA,
        DELETED_MEDIA,
        UPLOADED_MEDIA,
        UPLOADED_MULTIPLE_MEDIA, // these don't exist in FluxC, but are an artifact to wait for all
        // uploads to finish
        UPLOADED_MULTIPLE_MEDIA_WITH_CANCEL, // same as above
        NULL_ERROR,
        MALFORMED_ERROR,
        NOT_FOUND_ERROR,
        REMOVED_MEDIA
    }

    private TestEvents mNextEvent;
    private long mLastUploadedId = -1L;

    private List<Long> mUploadedIds = new ArrayList<>();
    private HashMap<Integer, MediaModel> mUploadedMediaModels = new HashMap<>();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mReleaseStackAppComponent.inject(this);

        // Register and initialize sSite
        init();
        mNextEvent = TestEvents.NONE;
    }

    public void testDeleteMedia() throws InterruptedException {
        // upload media to guarantee media exists
        MediaModel testMedia = newMediaModel(BuildConfig.TEST_LOCAL_IMAGE, MediaUtils.MIME_TYPE_IMAGE);
        mNextEvent = TestEvents.UPLOADED_MEDIA;
        uploadMedia(testMedia);

        // verify and set media ID
        assertTrue(mLastUploadedId >= 0);
        testMedia.setMediaId(mLastUploadedId);
        assertNotNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // delete media and verify it's not in the store
        mNextEvent = TestEvents.DELETED_MEDIA;
        deleteMedia(testMedia);
        assertNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));
    }

    public void testDeleteMediaThatDoesNotExist() throws InterruptedException {
        MediaModel testMedia = new MediaModel();
        testMedia.setMediaId(9999999L);
        mNextEvent = TestEvents.NOT_FOUND_ERROR;
        deleteMedia(testMedia);
    }

    public void testFetchMediaList() throws InterruptedException {
        // upload media to guarantee media exists
        MediaModel testMedia = newMediaModel(BuildConfig.TEST_LOCAL_IMAGE, MediaUtils.MIME_TYPE_IMAGE);
        mNextEvent = TestEvents.UPLOADED_MEDIA;
        uploadMedia(testMedia);

        // verify and set media ID
        assertTrue(mLastUploadedId >= 0);
        testMedia.setMediaId(mLastUploadedId);
        assertNotNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // remove all local media and verify store is empty
        mNextEvent = TestEvents.REMOVED_MEDIA;
        removeAllSiteMedia();
        assertTrue(mMediaStore.getAllSiteMedia(sSite).isEmpty());

        // fetch media list and verify store is not empty
        mNextEvent = TestEvents.FETCHED_MEDIA_LIST;
        fetchMediaList();
        assertFalse(mMediaStore.getAllSiteMedia(sSite).isEmpty());

        // delete test image
        mNextEvent = TestEvents.DELETED_MEDIA;
        deleteMedia(testMedia);
    }

    public void testFetchMediaThatDoesNotExist() throws InterruptedException {
        List<Long> mediaIds = new ArrayList<>();
        mediaIds.add(9999999L);
        mediaIds.add(9999989L);
        mNextEvent = TestEvents.NOT_FOUND_ERROR;
        for (Long id : mediaIds) {
            MediaModel media = new MediaModel();
            media.setMediaId(id);
            fetchMedia(media);
        }
    }

    public void testFetchMediaThatExists() throws InterruptedException {
        // upload media to guarantee media exists
        MediaModel testMedia = newMediaModel(BuildConfig.TEST_LOCAL_IMAGE, MediaUtils.MIME_TYPE_IMAGE);
        mNextEvent = TestEvents.UPLOADED_MEDIA;
        uploadMedia(testMedia);

        // verify and set media ID
        assertTrue(mLastUploadedId >= 0);
        testMedia.setMediaId(mLastUploadedId);
        assertNotNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // remove local media and verify it's not in the store
        mNextEvent = TestEvents.REMOVED_MEDIA;
        removeMedia(testMedia);
        assertNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // fetch test media from remote and verify it's in the store
        mNextEvent = TestEvents.FETCHED_MEDIA;
        fetchMedia(testMedia);
        assertNotNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // delete test image
        mNextEvent = TestEvents.DELETED_MEDIA;
        deleteMedia(testMedia);
    }

    public void testEditMedia() throws InterruptedException {
        // upload media to guarantee media exists
        MediaModel testMedia = newMediaModel(BuildConfig.TEST_LOCAL_IMAGE, MediaUtils.MIME_TYPE_IMAGE);
        mNextEvent = TestEvents.UPLOADED_MEDIA;
        uploadMedia(testMedia);

        // verify and set media ID
        assertTrue(mLastUploadedId >= 0);
        testMedia.setMediaId(mLastUploadedId);
        assertNotNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // create a random title and push changes
        testMedia.setTitle(RandomStringUtils.randomAlphabetic(8));
        mNextEvent = TestEvents.PUSHED_MEDIA;
        pushMedia(testMedia);

        // verify store media has been updated
        MediaModel storeMedia = mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId());
        assertNotNull(storeMedia);
        assertEquals(testMedia.getTitle(), storeMedia.getTitle());

        // delete test image
        mNextEvent = TestEvents.DELETED_MEDIA;
        deleteMedia(testMedia);
    }

    public void testUploadImage() throws InterruptedException {
        // upload media to guarantee media exists
        MediaModel testMedia = newMediaModel(BuildConfig.TEST_LOCAL_IMAGE, MediaUtils.MIME_TYPE_IMAGE);
        mNextEvent = TestEvents.UPLOADED_MEDIA;
        uploadMedia(testMedia);

        // verify and set media ID
        assertTrue(mLastUploadedId >= 0);
        testMedia.setMediaId(mLastUploadedId);
        assertNotNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // delete test image
        mNextEvent = TestEvents.DELETED_MEDIA;
        deleteMedia(testMedia);
    }

    public void testUploadMultipleImages() throws InterruptedException {
        // upload media to guarantee media exists
        mUploadedIds = new ArrayList<>();
        mNextEvent = TestEvents.UPLOADED_MULTIPLE_MEDIA;

        mUploadedMediaModels = new HashMap<>();
        // here we use the newMediaModel() with id builder, as we need it to identify uploads
        addMediaModelToUploadArray("Test media 1");
        addMediaModelToUploadArray("Test media 2");
        addMediaModelToUploadArray("Test media 3");
        addMediaModelToUploadArray("Test media 4");
        addMediaModelToUploadArray("Test media 5");

        // upload media, dispatching all at a time (not waiting for each to finish)
        // also don't cancel any upload (0)
        uploadMultipleMedia(new ArrayList<>(mUploadedMediaModels.values()), 0);

        // verify all have been uploaded
        assertEquals(mUploadedMediaModels.size(), mUploadedIds.size());

        // verify they exist in the MediaStore
        Iterator<MediaModel> iterator = mUploadedMediaModels.values().iterator();
        while (iterator.hasNext()) {
            MediaModel media = iterator.next();
            assertNotNull(mMediaStore.getSiteMediaWithId(sSite, media.getMediaId()));
        }

        // delete test images (bear in mind this is done sequentially)
        mNextEvent = TestEvents.DELETED_MEDIA;
        iterator = mUploadedMediaModels.values().iterator();
        while (iterator.hasNext()) {
            MediaModel media = iterator.next();
            deleteMedia(media);
        }
    }

    public void testUploadMultipleImagesAndCancel() throws InterruptedException {
        // upload media to guarantee media exists
        mUploadedIds = new ArrayList<>();
        mNextEvent = TestEvents.UPLOADED_MULTIPLE_MEDIA_WITH_CANCEL;

        mUploadedMediaModels = new HashMap<>();
        // here we use the newMediaModel() with id builder, as we need it to identify uploads
        addMediaModelToUploadArray("Test media 1");
        addMediaModelToUploadArray("Test media 2");
        addMediaModelToUploadArray("Test media 3");
        addMediaModelToUploadArray("Test media 4");
        addMediaModelToUploadArray("Test media 5");

        // use this variable to test cancelling 1, 2, 3, 4 or all 5 uploads
        int amountToCancel = 4;

        // upload media, dispatching all at a time (not waiting for each to finish)
        // also cancel the first n=`amountToCancel` media uploads
        uploadMultipleMedia(new ArrayList<>(mUploadedMediaModels.values()), amountToCancel);

        // verify how many have been uploaded
        assertEquals(mUploadedMediaModels.size() - amountToCancel, mUploadedIds.size());

        // verify each one of the remaining, non-cancelled uploads exist in the MediaStore
        Iterator<MediaModel> iterator = mUploadedMediaModels.values().iterator();
        while (iterator.hasNext()) {
            MediaModel media = iterator.next();
            assertNotNull(mMediaStore.getSiteMediaWithId(sSite, media.getMediaId()));
        }

        // delete test images (bear in mind this is done sequentially)
        mNextEvent = TestEvents.DELETED_MEDIA;
        iterator = mUploadedMediaModels.values().iterator();
        while (iterator.hasNext()) {
            MediaModel media = iterator.next();
            // delete only successfully uploaded test images
            if (mUploadedIds.contains(media.getMediaId())) {
                deleteMedia(media);
            }
        }
    }

    public void testUploadVideo() throws InterruptedException {
        // upload media to guarantee media exists
        MediaModel testMedia = newMediaModel(BuildConfig.TEST_LOCAL_VIDEO, MediaUtils.MIME_TYPE_VIDEO);
        mNextEvent = TestEvents.UPLOADED_MEDIA;
        uploadMedia(testMedia);

        // verify and set media ID
        assertTrue(mLastUploadedId >= 0);
        testMedia.setMediaId(mLastUploadedId);
        assertNotNull(mMediaStore.getSiteMediaWithId(sSite, testMedia.getMediaId()));

        // delete test image
        mNextEvent = TestEvents.DELETED_MEDIA;
        deleteMedia(testMedia);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onMediaUploaded(OnMediaUploaded event) throws InterruptedException {
        if (event.isError()) {
            throw new AssertionError("Unexpected error occurred with type: " + event.error.type);
        }
        if (event.canceled) {
            if (mNextEvent == TestEvents.UPLOADED_MULTIPLE_MEDIA_WITH_CANCEL) {
                assertEquals(TestEvents.UPLOADED_MULTIPLE_MEDIA_WITH_CANCEL, mNextEvent);
                mCountDownLatch.countDown();
            }
        } else if (event.completed) {
            if (mNextEvent == TestEvents.UPLOADED_MULTIPLE_MEDIA_WITH_CANCEL) {
                assertEquals(TestEvents.UPLOADED_MULTIPLE_MEDIA_WITH_CANCEL, mNextEvent);
                mUploadedIds.add(event.media.getMediaId());
                // now update our own map object with the new media id
                MediaModel media = mUploadedMediaModels.get(event.media.getId());
                if (media != null) {
                    media.setMediaId(event.media.getMediaId());
                } else {
                    AppLog.e(AppLog.T.MEDIA, "mediamodel not found: " + event.media.getId());
                }
                assertNotNull(media);
            } else if (mNextEvent == TestEvents.UPLOADED_MULTIPLE_MEDIA) {
                assertEquals(TestEvents.UPLOADED_MULTIPLE_MEDIA, mNextEvent);
                mUploadedIds.add(event.media.getMediaId());
                // now update our own map object with the new media id
                MediaModel media = mUploadedMediaModels.get(event.media.getId());
                if (media != null) {
                    media.setMediaId(event.media.getMediaId());
                } else {
                    AppLog.e(AppLog.T.MEDIA, "mediamodel not found: " + event.media.getId());
                }
                assertNotNull(media);
            } else
            if (mNextEvent == TestEvents.UPLOADED_MEDIA) {
                assertEquals(TestEvents.UPLOADED_MEDIA, mNextEvent);
                mLastUploadedId = event.media.getMediaId();
            }
            mCountDownLatch.countDown();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onMediaChanged(OnMediaChanged event) {
        AppLog.d(AppLog.T.TESTS, "tests.onMediaChanged: " + event.cause);

        if (event.isError()) {
            if (event.error.type == MediaErrorType.NULL_MEDIA_ARG) {
                assertEquals(TestEvents.NULL_ERROR, mNextEvent);
            } else if (event.error.type == MediaErrorType.MALFORMED_MEDIA_ARG) {
                assertEquals(TestEvents.MALFORMED_ERROR, mNextEvent);
            } else if (event.error.type == MediaErrorType.NOT_FOUND) {
                assertEquals(TestEvents.NOT_FOUND_ERROR, mNextEvent);
            } else {
                throw new AssertionError("Unexpected error occurred with type: " + event.error.type);
            }
        } else {
            if (event.cause == MediaAction.FETCH_MEDIA) {
                assertEquals(TestEvents.FETCHED_MEDIA, mNextEvent);
            } else if (event.cause == MediaAction.PUSH_MEDIA) {
                assertEquals(TestEvents.PUSHED_MEDIA, mNextEvent);
            } else if (event.cause == MediaAction.DELETE_MEDIA) {
                assertEquals(TestEvents.DELETED_MEDIA, mNextEvent);
            }
        }
        mCountDownLatch.countDown();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onMediaListFetched(OnMediaListFetched event) {
        if (event.isError()) {
            throw new AssertionError("Unexpected error occurred with type: " + event.error.type);
        }
        assertEquals(TestEvents.FETCHED_MEDIA_LIST, mNextEvent);
        mCountDownLatch.countDown();
    }

    private void addMediaModelToUploadArray(String title) {
        MediaModel mediaModel = newMediaModel(title, BuildConfig.TEST_LOCAL_IMAGE, MediaUtils.MIME_TYPE_IMAGE);
        mUploadedMediaModels.put(mediaModel.getId(), mediaModel);
    }

    private MediaModel newMediaModel(String mediaPath, String mimeType) {
        return newMediaModel("Test Title", mediaPath, mimeType);
    }

    private MediaModel newMediaModel(String testTitle, String mediaPath, String mimeType) {
        final String testDescription = "Test Description";
        final String testCaption = "Test Caption";
        final String testAlt = "Test Alt";

        MediaModel testMedia = mMediaStore.instantiateMediaModel();
        testMedia.setFilePath(mediaPath);
        testMedia.setFileExtension(mediaPath.substring(mediaPath.lastIndexOf(".") + 1, mediaPath.length()));
        testMedia.setMimeType(mimeType + testMedia.getFileExtension());
        testMedia.setFileName(mediaPath.substring(mediaPath.lastIndexOf("/"), mediaPath.length()));
        testMedia.setTitle(testTitle);
        testMedia.setDescription(testDescription);
        testMedia.setCaption(testCaption);
        testMedia.setAlt(testAlt);
        testMedia.setLocalSiteId(sSite.getId());

        return testMedia;
    }

    private void pushMedia(MediaModel media) throws InterruptedException {
        MediaStore.MediaPayload payload = new MediaStore.MediaPayload(sSite, media);
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(MediaActionBuilder.newPushMediaAction(payload));
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void fetchMediaList() throws InterruptedException {
        FetchMediaListPayload fetchPayload = new FetchMediaListPayload(sSite, false);
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(MediaActionBuilder.newFetchMediaListAction(fetchPayload));
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void fetchMedia(MediaModel media) throws InterruptedException {
        MediaStore.MediaPayload fetchPayload = new MediaStore.MediaPayload(sSite, media, null);
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(MediaActionBuilder.newFetchMediaAction(fetchPayload));
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void uploadMedia(MediaModel media) throws InterruptedException {
        MediaStore.MediaPayload payload = new MediaStore.MediaPayload(sSite, media);
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(MediaActionBuilder.newUploadMediaAction(payload));
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void uploadMultipleMedia(List<MediaModel> mediaList, int howManyFirstToCancel) throws InterruptedException {
        mCountDownLatch = new CountDownLatch(mediaList.size());
        for (MediaModel media : mediaList) {
            MediaStore.MediaPayload payload = new MediaStore.MediaPayload(sSite, media);
            mDispatcher.dispatch(MediaActionBuilder.newUploadMediaAction(payload));
        }

        if (howManyFirstToCancel > 0 && howManyFirstToCancel <= mediaList.size()) {
            // wait a bit and issue the cancel command
            TestUtils.waitFor(1000);

            // we'e only cancelling the first n=howManyFirstToCancel uploads
            for (int i = 0; i < howManyFirstToCancel; i++) {
                MediaModel media = mediaList.get(i);
                MediaStore.MediaPayload payload = new MediaStore.MediaPayload(sSite, media);
                mDispatcher.dispatch(MediaActionBuilder.newCancelMediaUploadAction(payload));
            }
        }

        assertTrue(mCountDownLatch.await(TestUtils.MULTIPLE_UPLOADS_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void deleteMedia(MediaModel media) throws InterruptedException {
        MediaStore.MediaPayload deletePayload = new MediaStore.MediaPayload(sSite, media);
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(MediaActionBuilder.newDeleteMediaAction(deletePayload));
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void removeMedia(MediaModel media) throws InterruptedException {
        mCountDownLatch = new CountDownLatch(1);
        mDispatcher.dispatch(MediaActionBuilder.newRemoveMediaAction(media));
        assertTrue(mCountDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void removeAllSiteMedia() throws InterruptedException {
        List<MediaModel> allMedia = mMediaStore.getAllSiteMedia(sSite);
        if (!allMedia.isEmpty()) {
            for (MediaModel media : allMedia) {
                removeMedia(media);
            }
        }
    }
}
