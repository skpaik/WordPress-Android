package org.wordpress.android.ui.posts.photochooser;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.wordpress.android.R;
import org.wordpress.android.ui.posts.EditPostActivity;
import org.wordpress.android.util.AniUtils;
import org.wordpress.android.util.DisplayUtils;

import java.util.ArrayList;

public class PhotoChooserFragment extends Fragment {

    private static final int NUM_COLUMNS = 3;
    private static final String KEY_MULTI_SELECT_ENABLED = "multi_select_enabled";
    private static final String KEY_SELECTED_IMAGE_LIST = "selected_image_list";

    enum PhotoChooserIcon {
        ANDROID_CAMERA,
        ANDROID_PICKER,
        WP_MEDIA
    }

    public interface OnPhotoChooserListener {
        void onPhotoTapped(View view, Uri imageUri);
        void onPhotoDoubleTapped(View view, Uri imageUri);
        void onPhotoLongPressed(View view, Uri imageUri);
    }

    private RecyclerView mRecycler;
    private PhotoChooserAdapter mAdapter;
    private View mBottomBar;
    private ActionMode mActionMode;

    public static PhotoChooserFragment newInstance() {
        Bundle args = new Bundle();
        PhotoChooserFragment fragment = new PhotoChooserFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(KEY_MULTI_SELECT_ENABLED)) {
                getAdapter().setMultiSelectEnabled(true);
                restoreSelectedImages(savedInstanceState);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (isMultiSelectEnabled()) {
            outState.putBoolean(KEY_MULTI_SELECT_ENABLED, true);
            saveSelectedImages(outState);
        }
    }

    private void restoreSelectedImages(Bundle savedInstanceState) {
        if (!savedInstanceState.containsKey(KEY_SELECTED_IMAGE_LIST)) return;

        ArrayList<String> strings = savedInstanceState.getStringArrayList(KEY_SELECTED_IMAGE_LIST);
        if (strings == null || strings.size() == 0) return;

        ArrayList<Uri> uriList = new ArrayList<>();
        for (String stringUri: strings) {
            uriList.add(Uri.parse(stringUri));
        }
        getAdapter().setSelectedImageURIs(uriList);
    }

    private void saveSelectedImages(Bundle outState) {
        ArrayList<Uri> uriList = getAdapter().getSelectedImageURIs();
        if (uriList.size() == 0) return;

        ArrayList<String> stringUris = new ArrayList<>();
        for (Uri uri: uriList) {
            stringUris.add(uri.toString());
        }
        outState.putStringArrayList(KEY_SELECTED_IMAGE_LIST, stringUris);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.photo_chooser_fragment, container, false);

        mBottomBar = view.findViewById(R.id.bottom_bar);

        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), NUM_COLUMNS);

        mRecycler = (RecyclerView) view.findViewById(R.id.recycler);
        mRecycler.setHasFixedSize(true);
        mRecycler.setLayoutManager(layoutManager);

        mBottomBar.findViewById(R.id.icon_camera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleIconClicked(PhotoChooserIcon.ANDROID_CAMERA);
            }
        });

        mBottomBar.findViewById(R.id.icon_picker).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleIconClicked(PhotoChooserIcon.ANDROID_PICKER);
            }
        });

        mBottomBar.findViewById(R.id.icon_wpmedia).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleIconClicked(PhotoChooserIcon.WP_MEDIA);
            }
        });

        loadDevicePhotos();

        return view;
    }

    /*
     * returns the EditPostActivity that hosts this fragment - obviously we're assuming
     * this fragment will always be hosted in an EditPostActivity, if this assumption
     * changes then we'll need to change this fragment accordingly
     */
    private EditPostActivity getEditPostActivity() {
        return (EditPostActivity) getActivity();
    }

    private void handleIconClicked(PhotoChooserIcon icon) {
        EditPostActivity activity = getEditPostActivity();
        activity.hidePhotoChooser();
        switch (icon) {
            case ANDROID_CAMERA:
                activity.launchCamera();
                break;
            case ANDROID_PICKER:
                activity.launchPictureLibrary();
                break;
            case WP_MEDIA:
                activity.startMediaGalleryAddActivity();
                break;
        }
    }

    private void showBottomBar() {
        if (!isBottomBarShowing()) {
            AniUtils.animateBottomBar(mBottomBar, true);
        }
    }

    private void hideBottomBar() {
        if (isBottomBarShowing()) {
            AniUtils.animateBottomBar(mBottomBar, false);
        }
    }

    private boolean isBottomBarShowing() {
        return mBottomBar.getVisibility() == View.VISIBLE;
    }

    /*
     *   - single tap adds the photo to post or selects it if multi-select is enabled
     *   - double tap previews the photo
     *   - long press enables multi-select
     */
    private final OnPhotoChooserListener mListener = new OnPhotoChooserListener() {
        @Override
        public void onPhotoTapped(View view, Uri imageUri) {
            if (isMultiSelectEnabled()) {
                togglePhotoSelection(imageUri);
            } else {
                EditPostActivity activity = getEditPostActivity();
                activity.addMedia(imageUri);
                activity.hidePhotoChooser();
            }
        }

        @Override
        public void onPhotoLongPressed(View view, Uri imageUri) {
            if (isMultiSelectEnabled()) {
                togglePhotoSelection(imageUri);
            } else {
                enableMultiSelect(imageUri);
            }
        }

        @Override
        public void onPhotoDoubleTapped(View view, Uri imageUri) {
            showPreview(view, imageUri);
        }
    };

    /*
     * shows full-screen preview of the passed media
     */
    private void showPreview(View sourceView, Uri imageUri) {
        Intent intent = new Intent(getActivity(), PhotoChooserPreviewActivity.class);
        intent.putExtra(PhotoChooserPreviewActivity.ARG_MEDIA_URI, imageUri.toString());

        int startWidth = sourceView.getWidth();
        int startHeight = sourceView.getHeight();
        int startX = startWidth / 2;
        int startY = startHeight / 2;

        ActivityOptionsCompat options = ActivityOptionsCompat.makeScaleUpAnimation(
                sourceView,
                startX,
                startY,
                startWidth,
                startHeight);
        ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
    }

    private boolean isMultiSelectEnabled() {
        return mAdapter != null && mAdapter.isMultiSelectEnabled();
    }

    private void enableMultiSelect(Uri imageUri) {
        getAdapter().setMultiSelectEnabled(true);
        getAdapter().togglePhotoSelection(imageUri);
        ((AppCompatActivity) getActivity()).startSupportActionMode(new ActionModeCallback());
        updateActionModeTitle();
    }

    private void togglePhotoSelection(Uri imageUri) {
        getAdapter().togglePhotoSelection(imageUri);
        updateActionModeTitle();
        if (getAdapter().getNumSelected() == 0) {
            finishActionMode();
        }
    }

    private PhotoChooserAdapter getAdapter() {
        if (mAdapter == null) {
            int displayWidth = DisplayUtils.getDisplayPixelWidth(getActivity());
            int imageWidth = displayWidth / NUM_COLUMNS;
            int imageHeight = (int) (imageWidth * 0.75f);
            mAdapter = new PhotoChooserAdapter(
                    getActivity(), imageWidth, imageHeight, mListener);
        }
        return mAdapter;
    }

    /*
     * populates the adapter with photos stored on the device
     */
    private void loadDevicePhotos() {
        mRecycler.setAdapter(getAdapter());
        getAdapter().loadDevicePhotos();
    }

    /*
     * inserts the passed list of images into the post and closes the photo chooser
     */
    private void insertPhotos(ArrayList<Uri> uriList) {
        EditPostActivity activity = getEditPostActivity();
        for (Uri uri: uriList) {
            activity.addMedia(uri);
        }

        activity.hidePhotoChooser();
    }

    public void finishActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    private void updateActionModeTitle() {
        if (mActionMode == null) return;

        int numSelected = getAdapter().getNumSelected();
        String title = String.format(getString(R.string.cab_selected), numSelected);
        mActionMode.setTitle(title);
    }

    private final class ActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            mActionMode = actionMode;
            MenuInflater inflater = actionMode.getMenuInflater();
            inflater.inflate(R.menu.photo_chooser_action_mode, menu);
            hideBottomBar();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.mnu_confirm_selection) {
                ArrayList<Uri> uriList = getAdapter().getSelectedImageURIs();
                insertPhotos(uriList);
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            getAdapter().setMultiSelectEnabled(false);
            mActionMode = null;
            showBottomBar();
        }
    }
}
