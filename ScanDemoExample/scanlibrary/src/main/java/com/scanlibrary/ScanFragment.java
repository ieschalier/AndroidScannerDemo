package com.scanlibrary;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ScaleImageView;

import org.opencv.core.Point;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScanFragment extends Fragment {


    // ===========================================================
    // Constants
    // ===========================================================

    public static final String RESULT_IMAGE_PATH = "imgPath";

    private static final int TAKE_PHOTO_REQUEST_CODE = 815;
    private static final String SAVED_ARG_TAKEN_PHOTO_LOCATION = "taken_photo_loc";

    // ===========================================================
    // Fields
    // ===========================================================

    private ViewHolder viewHolder = new ViewHolder();
    private ProgressDialogFragment progressDialogFragment;

    private String takenPhotoLocation;
    private Bitmap takenPhotoBitmap;
    private Bitmap documentBitmap;
    private Bitmap documentColoredBitmap;

    private Map<Integer, PointF> points;

    private boolean isCropMode = false;

    private int previousOreantation = -1;

    // ===========================================================
    // Constructors
    // ===========================================================

    // ===========================================================
    // Getters & Setters
    // ===========================================================

    // ===========================================================
    // Methods for/from SuperClass/Interfaces
    // ===========================================================


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        viewHolder.prepare(view);
        super.onViewCreated(view, savedInstanceState);

        int currentOreantation = Utils.getScreenOrientation(getActivity());
        if (previousOreantation == -1) {
            previousOreantation = currentOreantation;
        } else if (previousOreantation != currentOreantation) {
            points = null;
        }

        if (takenPhotoLocation == null) {
            takePhoto();
        } else {
            if (documentBitmap != null) {
                updateViewsWithNewBitmap();
                viewHolder.sourceImageView.setVisibility(View.INVISIBLE);
                viewHolder.scaleImageView.setVisibility(View.VISIBLE);
            }
        }

        if (isCropMode) {
            viewHolder.sourceFrame.post(new Runnable() {
                @Override
                public void run() {
                    Bitmap scaledBitmap = ImageResizer.scaleBitmap(takenPhotoBitmap, viewHolder.sourceFrame.getWidth(), viewHolder.sourceFrame.getHeight());
                    viewHolder.sourceImageView.setImageBitmap(scaledBitmap);

                    Bitmap tempBitmap = ((BitmapDrawable) viewHolder.sourceImageView.getDrawable()).getBitmap();
                    viewHolder.polygonView.setVisibility(View.VISIBLE);
                    int padding = (int) getResources().getDimension(R.dimen.scanPadding);
                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
                    layoutParams.gravity = Gravity.CENTER;
                    viewHolder.polygonView.setLayoutParams(layoutParams);

                    if (points == null) {
                        points = getOutlinePoints(tempBitmap);
                    }
                    viewHolder.polygonView.setPoints(points);
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == TAKE_PHOTO_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                onPhotoTaken();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                removeFile(takenPhotoLocation);
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(SAVED_ARG_TAKEN_PHOTO_LOCATION, takenPhotoLocation);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.scan_menu, menu);

        MenuItem cropBtn = menu.findItem(R.id.crop);
        MenuItem rotateBtn = menu.findItem(R.id.rotate);
        MenuItem modeBtn = menu.findItem(R.id.colors);

        cropBtn.setVisible(!isCropMode);
        rotateBtn.setVisible(!isCropMode);
        modeBtn.setVisible(!isCropMode);

        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.crop) {
            onCropButtonClicked();
            return true;
        } else if (item.getItemId() == R.id.done) {
            onDoneButtonClicked();
            return true;
        } else if (item.getItemId() == R.id.rotate) {
            onRotateButtonClicked();
            return true;
        } else if (item.getItemId() == R.id.mode_none) {
            if (!item.isChecked()) {
                item.setChecked(true);
                onNoneModeChosen();
            }

            return true;
        } else if (item.getItemId() == R.id.mode_black_and_white) {
            if (!item.isChecked()) {
                item.setChecked(true);
                onBlackAndWhiteModeChosen();
            }

            return true;
        } else if (item.getItemId() == R.id.mode_magic) {
            if (!item.isChecked()) {
                item.setChecked(true);
                onMagicModeChosen();
            }

            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    // ===========================================================
    // Methods
    // ===========================================================
    private void onMagicModeChosen() {
        documentColoredBitmap = ScanUtils.getMagicColorBitmap(documentBitmap);
        updateViewsWithNewBitmap();
    }

    private void onBlackAndWhiteModeChosen() {
        documentColoredBitmap = ScanUtils.getGrayBitmap(documentBitmap);
        updateViewsWithNewBitmap();
    }

    private void onNoneModeChosen() {
        if (documentColoredBitmap != null) {
            documentColoredBitmap.recycle();
            documentColoredBitmap = null;
        }
        updateViewsWithNewBitmap();
    }

    private void updateViewsWithNewBitmap() {
        Bitmap tmp = documentColoredBitmap != null ? documentColoredBitmap : documentBitmap;
        tmp = ImageResizer.scaleBitmap(tmp, viewHolder.sourceFrame.getWidth(), viewHolder.sourceFrame.getHeight());
        viewHolder.sourceImageView.setImageBitmap(tmp);
        viewHolder.scaleImageView.setImageBitmap(tmp);
    }

    /**
     * Called From activity
     */
    public boolean onBackPressed() {
        if (isCropMode) {
            updateViewsWithNewBitmap();
            viewHolder.sourceImageView.setVisibility(View.INVISIBLE);
            viewHolder.scaleImageView.setVisibility(View.VISIBLE);
            viewHolder.polygonView.setVisibility(View.GONE);

            getActivity().invalidateOptionsMenu();
            isCropMode = false;
            return false;
        }

        releaseAllBitmaps();
        if (takenPhotoLocation != null) {
            removeFile(takenPhotoLocation);
        }

        return true;
    }

    private void releaseAllBitmaps() {
        if (takenPhotoBitmap != null) takenPhotoBitmap.recycle();
        if (documentBitmap != null) documentBitmap.recycle();
    }

    private void onCropButtonClicked() {
        getActivity().invalidateOptionsMenu();
        isCropMode = true;

        Bitmap scaledBitmap = ImageResizer.scaleBitmap(takenPhotoBitmap, viewHolder.sourceFrame.getWidth(), viewHolder.sourceFrame.getHeight());
        viewHolder.sourceImageView.setImageBitmap(scaledBitmap);
        viewHolder.sourceImageView.setVisibility(View.VISIBLE);
        viewHolder.scaleImageView.setVisibility(View.GONE);

        Bitmap tempBitmap = ((BitmapDrawable) viewHolder.sourceImageView.getDrawable()).getBitmap();
        viewHolder.polygonView.setVisibility(View.VISIBLE);

        Map<Integer, PointF> pointsToUse = null;
        if (points == null) {
            pointsToUse = getEdgePoints(tempBitmap);
        } else {
            pointsToUse = downScalePoints(points, takenPhotoBitmap, tempBitmap.getWidth(), tempBitmap.getHeight());
        }

        viewHolder.polygonView.setPoints(pointsToUse);
        int padding = (int) getResources().getDimension(R.dimen.scanPadding);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(tempBitmap.getWidth() + 2 * padding, tempBitmap.getHeight() + 2 * padding);
        layoutParams.gravity = Gravity.CENTER;
        viewHolder.polygonView.setLayoutParams(layoutParams);
    }


    private void onRotateButtonClicked() {
        Bitmap takenPhotoBitmapTmp = Utils.rotateBitmap(takenPhotoBitmap, -90);
        takenPhotoBitmap.recycle();
        takenPhotoBitmap = takenPhotoBitmapTmp;

        Bitmap documentBitmapTmp = Utils.rotateBitmap(documentBitmap, -90);
        documentBitmap.recycle();
        documentBitmap = documentBitmapTmp;

        if (documentColoredBitmap != null) {
            Bitmap documentColoredBitmapTmp = Utils.rotateBitmap(documentColoredBitmap, -90);
            documentColoredBitmap.recycle();
            documentColoredBitmap = documentColoredBitmapTmp;
        }

        Bitmap scaledBitmap = ImageResizer.scaleBitmap(documentColoredBitmap != null ? documentColoredBitmap : documentBitmap, viewHolder.sourceFrame.getWidth(), viewHolder.sourceFrame.getHeight());
        viewHolder.sourceImageView.setImageBitmap(scaledBitmap);
        viewHolder.sourceImageView.setVisibility(View.INVISIBLE);
        viewHolder.scaleImageView.setImageBitmap(scaledBitmap);
        viewHolder.scaleImageView.setVisibility(View.VISIBLE);
//        Bitmap tempBitmap = ((BitmapDrawable) viewHolder.sourceImageView.getDrawable()).getBitmap();
        points = getOutlinePoints(viewHolder.sourceFrame);
    }


    private void onDoneButtonClicked() {
        if (isCropMode) {
            isCropMode = false;
            getActivity().invalidateOptionsMenu();

            Map<Integer, PointF> points = viewHolder.polygonView.getPoints();
            if (isScanPointsValid(points)) {
                upScalePoints(points, takenPhotoBitmap, viewHolder.sourceImageView.getWidth(), viewHolder.sourceImageView.getHeight());
                new DocumentFromBitmapTask(takenPhotoBitmap, points).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                showErrorDialog();
            }

        } else {
            File scannedDocFile = createImageFile("scanned_doc");

            Bitmap tmp = documentColoredBitmap != null ? documentColoredBitmap : documentColoredBitmap;

            try {
                tmp = ImageResizer.resizeImage(tmp, 2048, 2048);
            } catch (IOException e) {
                e.printStackTrace();
            }

            saveBitmapToFile(scannedDocFile, tmp);
            removeFile(takenPhotoLocation);
            releaseAllBitmaps();

            Intent intent = new Intent();
            intent.putExtra(RESULT_IMAGE_PATH, scannedDocFile.getAbsolutePath());
            getActivity().setResult(Activity.RESULT_OK, intent);
            getActivity().finish();
        }
    }

    private boolean saveBitmapToFile(File file, Bitmap bitmap) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(CompressFormat.JPEG, 100, out);
            // PNG is a lossless format, the compression factor (100) is ignored
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        return true;
    }

    private void onPhotoTaken() {
        takenPhotoBitmap = getBitmapFromLocation(takenPhotoLocation);

        DocumentFromBitmapTask documentFromBitmapTask = new DocumentFromBitmapTask(takenPhotoBitmap, null);
        documentFromBitmapTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static Bitmap cropDocumentFromBitmap(Bitmap bitmap, Map<Integer, PointF> points) {
        float x1 = (points.get(0).x);
        float x2 = (points.get(1).x);
        float x3 = (points.get(2).x);
        float x4 = (points.get(3).x);
        float y1 = (points.get(0).y);
        float y2 = (points.get(1).y);
        float y3 = (points.get(2).y);
        float y4 = (points.get(3).y);
        Log.d("", "POints(" + x1 + "," + y1 + ")(" + x2 + "," + y2 + ")(" + x3 + "," + y3 + ")(" + x4 + "," + y4 + ")");
        return ScanUtils.getScannedBitmap(bitmap, x1, y1, x2, y2, x3, y3, x4, y4);
    }

    private static void upScalePoints(Map<Integer, PointF> points, Bitmap original, int scaledImgWidth, int scaledImgHeight) {
        int width = original.getWidth();
        int height = original.getHeight();
        float xRatio = (float) width / scaledImgWidth;
        float yRatio = (float) height / scaledImgHeight;

        points.get(0).x = (points.get(0).x) * xRatio;
        points.get(1).x = (points.get(1).x) * xRatio;
        points.get(2).x = (points.get(2).x) * xRatio;
        points.get(3).x = (points.get(3).x) * xRatio;
        points.get(0).y = (points.get(0).y) * yRatio;
        points.get(1).y = (points.get(1).y) * yRatio;
        points.get(2).y = (points.get(2).y) * yRatio;
        points.get(3).y = (points.get(3).y) * yRatio;
    }

    private static Map<Integer, PointF> downScalePoints(Map<Integer, PointF> points, Bitmap original, int targetImgWidth, int targetImgHeight) {
        int width = original.getWidth();
        int height = original.getHeight();
        float xRatio = (float) width / targetImgWidth;
        float yRatio = (float) height / targetImgHeight;

        Map<Integer, PointF> scaledPoints = new HashMap<>(4);
        scaledPoints.put(0, new PointF((points.get(0).x) / xRatio, (points.get(0).y) / yRatio));
        scaledPoints.put(1, new PointF((points.get(1).x) / xRatio, (points.get(1).y) / yRatio));
        scaledPoints.put(2, new PointF((points.get(2).x) / xRatio, (points.get(2).y) / yRatio));
        scaledPoints.put(3, new PointF((points.get(3).x) / xRatio, (points.get(3).y) / yRatio));
        return scaledPoints;
    }

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File photoFile = createImageFile("takendocphoto");
        takenPhotoLocation = photoFile.getAbsolutePath();

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
        startActivityForResult(takePictureIntent, TAKE_PHOTO_REQUEST_CODE);
    }

    private File createImageFile(String fileName) {
        File storageDir = getActivity().getExternalFilesDir("images");
        if (storageDir == null) {
            throw new RuntimeException("Not able to get to External storage");
        }
        File image = new File(storageDir, fileName + ".jpg");

        return image;
    }

    private void removeFile(String absoluteLocation) {
        if (absoluteLocation == null) return;

        File f = new File(absoluteLocation);
        if (f.exists()) {
            f.delete();
        }
    }

    private Bitmap getBitmapFromLocation(String absLocation) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(absLocation, options);
    }

    private static Map<Integer, PointF> getEdgePoints(Bitmap tempBitmap) {
        List<PointF> pointFs = getContourEdgePoints(tempBitmap);
        Map<Integer, PointF> orderedPoints = orderedValidEdgePoints(tempBitmap, pointFs);
        return orderedPoints;
    }

    private static List<PointF> getContourEdgePoints(Bitmap tempBitmap) {
//        float[] points = ScanActivity.getPoints(tempBitmap);
//        float x1 = points[0];
//        float x2 = points[1];
//        float x3 = points[2];
//        float x4 = points[3];
//
//        float y1 = points[4];
//        float y2 = points[5];
//        float y3 = points[6];
//        float y4 = points[7];
//
//        List<PointF> pointFs = new ArrayList<>();
//        pointFs.add(new PointF(x1, y1));
//        pointFs.add(new PointF(x2, y2));
//        pointFs.add(new PointF(x3, y3));
//        pointFs.add(new PointF(x4, y4));
//        return pointFs;
        List<Point> points = ScanUtils.getPoints(tempBitmap);

        List<PointF> p = new ArrayList<>();
        p.add(new PointF((float) points.get(0).x, (float) points.get(0).y));
        p.add(new PointF((float) points.get(1).x, (float) points.get(1).y));
        p.add(new PointF((float) points.get(2).x, (float) points.get(2).y));
        p.add(new PointF((float) points.get(3).x, (float) points.get(3).y));

        return p;
    }

    private static Map<Integer, PointF> getOutlinePoints(Bitmap tempBitmap) {
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(0, 0));
        outlinePoints.put(1, new PointF(tempBitmap.getWidth(), 0));
        outlinePoints.put(2, new PointF(0, tempBitmap.getHeight()));
        outlinePoints.put(3, new PointF(tempBitmap.getWidth(), tempBitmap.getHeight()));
        return outlinePoints;
    }

    private static Map<Integer, PointF> getOutlinePoints(View view) {
        Map<Integer, PointF> outlinePoints = new HashMap<>();
        outlinePoints.put(0, new PointF(0, 0));
        outlinePoints.put(1, new PointF(view.getWidth(), 0));
        outlinePoints.put(2, new PointF(0, view.getHeight()));
        outlinePoints.put(3, new PointF(view.getWidth(), view.getHeight()));
        return outlinePoints;
    }

    private static Map<Integer, PointF> orderedValidEdgePoints(Bitmap tempBitmap, List<PointF> pointFs) {
        Map<Integer, PointF> orderedPoints = PolygonView.getOrderedPoints(pointFs);
        if (!PolygonView.isValidShape(orderedPoints)) {
            orderedPoints = getOutlinePoints(tempBitmap);
        }
        return orderedPoints;
    }

    private void showErrorDialog() {
        Toast.makeText(getActivity(), R.string.cant_crop, Toast.LENGTH_LONG).show();
    }

    private boolean isScanPointsValid(Map<Integer, PointF> points) {
        return points.size() == 4;
    }


    protected void showProgressDialog() {
        Bundle args = new Bundle();
        args.putString(ProgressDialogFragment.EXTRA_MESSAGE, getString(R.string.transforming));
        progressDialogFragment = new ProgressDialogFragment();
        progressDialogFragment.setArguments(args);
        progressDialogFragment.show(getFragmentManager(), "progress_dialog");
    }

    protected void dismissDialog() {
        ProgressDialogFragment progressDialogFragment = (ProgressDialogFragment) getFragmentManager().findFragmentByTag("progress_dialog");
        if (progressDialogFragment != null) progressDialogFragment.dismissAllowingStateLoss();
    }

    private void onDocumentFromBitmapTaskFinished(DocumentFromBitmapTaskResult result) {
        documentBitmap = result.bitmap;
        points = result.points;

        Bitmap scaledBitmap = ImageResizer.scaleBitmap(documentBitmap, viewHolder.sourceFrame.getWidth(), viewHolder.sourceFrame.getHeight());
        viewHolder.sourceImageView.setImageBitmap(scaledBitmap);
        viewHolder.sourceImageView.setVisibility(View.INVISIBLE);
        viewHolder.scaleImageView.setImageBitmap(scaledBitmap);
        viewHolder.scaleImageView.setVisibility(View.VISIBLE);

        viewHolder.polygonView.setVisibility(View.GONE);
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================

    private class DocumentFromBitmapTask extends AsyncTask<Void, Void, DocumentFromBitmapTaskResult> {

        private final Bitmap bitmap;
        private final Map<Integer, PointF> points;

        public DocumentFromBitmapTask(Bitmap bitmap, Map<Integer, PointF> points) {
            this.bitmap = bitmap;
            this.points = points;
        }

        @Override
        protected void onPreExecute() {
            showProgressDialog();
        }

        @Override
        protected DocumentFromBitmapTaskResult doInBackground(Void... params) {
            System.gc();
            DocumentFromBitmapTaskResult result = new DocumentFromBitmapTaskResult();

            if (points != null) {
                result.points = points;
                result.bitmap = cropDocumentFromBitmap(bitmap, points);
            } else {
                try {
                    Bitmap scaledBmp = ImageResizer.resizeImage(bitmap, 400, 400);
                    result.points = getEdgePoints(scaledBmp);
                    upScalePoints(result.points, bitmap, scaledBmp.getWidth(), scaledBmp.getHeight());
                    result.bitmap = cropDocumentFromBitmap(bitmap, result.points);
                } catch (IOException e) {
                    throw new RuntimeException("Error happen while cropping image");
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(DocumentFromBitmapTaskResult documentFromBitmapTaskResult) {
            onDocumentFromBitmapTaskFinished(documentFromBitmapTaskResult);
            dismissDialog();
        }
    }

    private static class DocumentFromBitmapTaskResult {
        Bitmap bitmap;
        Map<Integer, PointF> points;
    }

    private static class ViewHolder {
        private ImageView sourceImageView;
        private ScaleImageView scaleImageView;
        private FrameLayout sourceFrame;
        private PolygonView polygonView;

        void prepare(View parent) {
            sourceImageView = (ImageView) parent.findViewById(R.id.sourceImageView);
            scaleImageView = (ScaleImageView) parent.findViewById(R.id.scaleImage);
            sourceFrame = (FrameLayout) parent.findViewById(R.id.sourceFrame);
            polygonView = (PolygonView) parent.findViewById(R.id.polygonView);
        }
    }


}
