package org.qii.weiciyuan.ui.browser;

import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.*;
import android.webkit.WebView;
import android.widget.ShareActionProvider;
import android.widget.Toast;
import org.qii.weiciyuan.R;
import org.qii.weiciyuan.bean.MessageBean;
import org.qii.weiciyuan.support.asyncdrawable.TaskCache;
import org.qii.weiciyuan.support.file.FileDownloaderHttpHelper;
import org.qii.weiciyuan.support.file.FileLocationMethod;
import org.qii.weiciyuan.support.file.FileManager;
import org.qii.weiciyuan.support.imagetool.ImageTool;
import org.qii.weiciyuan.support.lib.CircleProgressView;
import org.qii.weiciyuan.support.lib.MyAsyncTask;
import org.qii.weiciyuan.support.utils.Utility;
import org.qii.weiciyuan.ui.interfaces.AbstractAppActivity;
import org.qii.weiciyuan.ui.main.MainTimeLineActivity;

import java.io.File;
import java.util.List;

/**
 * User: qii
 * Date: 12-8-18
 */
public class BrowserBigPicActivity extends AbstractAppActivity {


    private MenuItem oriMenu;
    private WebView webView;
    private CircleProgressView pb;
    private PicSimpleBitmapWorkerTask task;
    private PicSaveTask saveTask;
    private String path;
    private ShareActionProvider mShareActionProvider;
    private MessageBean msg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.browserbigpicactivity_layout);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(R.string.browser_picture);

        pb = (CircleProgressView) findViewById(R.id.pb);

        webView = (WebView) findViewById(R.id.iv);

        webView.setBackgroundColor(getResources().getColor(R.color.transparent));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOnTouchListener(new View.OnTouchListener() {
            boolean mPressed;
            boolean mClose;
            CheckForSinglePress mPendingCheckForSinglePress = new CheckForSinglePress();
            long lastTime = 0;
            float[] location = new float[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mPressed = true;
                        if (System.currentTimeMillis() - lastTime > ViewConfiguration.getDoubleTapTimeout()) {
                            mClose = true;
                            new Handler().postDelayed(mPendingCheckForSinglePress,
                                    ViewConfiguration.getDoubleTapTimeout());
                        } else {
                            mClose = false;
                        }
                        lastTime = System.currentTimeMillis();

                        location[0] = event.getRawX();
                        location[1] = event.getRawY();

                        break;
                    case MotionEvent.ACTION_UP:
                        mPressed = false;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        mPressed = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float x = event.getRawX();
                        float y = event.getRawY();
                        if (Math.abs(location[0] - x) > 5.0f && Math.abs(location[1] - y) > 5.0f) {
                            mClose = false;
                        }
                        break;
                }

                return false;
            }


            class CheckForSinglePress implements Runnable {

                public void run() {

                    if (!mPressed && mClose)
                        finish();

                }

            }
        });

        msg = (MessageBean) getIntent().getSerializableExtra("msg");
        if (Utility.isTaskStopped(task)) {
            task = new PicSimpleBitmapWorkerTask();
            task.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("msg", msg);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.actionbar_menu_browserbigpicactivity, menu);
        MenuItem item = menu.findItem(R.id.menu_share);
        mShareActionProvider = (ShareActionProvider) item.getActionProvider();
        oriMenu = menu.findItem(R.id.menu_switch);
        String largePath = FileManager.getFilePathFromUrl(msg.getOriginal_pic(), FileLocationMethod.picture_large);
        if (new File(largePath).exists()) {
            oriMenu.setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, MainTimeLineActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            case R.id.menu_switch:
                if (task != null) {
                    task.cancel(true);
                }

                task = new PicSimpleBitmapWorkerTask();
                task.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
                path = "";
                oriMenu.setVisible(false);
                getActionBar().setSubtitle(null);
                break;
            case R.id.menu_save:
                if (task != null && task.getStatus() == MyAsyncTask.Status.FINISHED) {
                    if (saveTask == null) {
                        saveTask = new PicSaveTask();
                        saveTask.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
                    } else if (saveTask.getStatus() == MyAsyncTask.Status.FINISHED) {
                        Toast.makeText(BrowserBigPicActivity.this, getString(R.string.already_saved), Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case R.id.menu_share:

                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("image/jpeg");

                if (!TextUtils.isEmpty(path)) {
                    Uri uri = Uri.fromFile(new File(path));
                    sharingIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    PackageManager packageManager = getPackageManager();
                    List<ResolveInfo> activities = packageManager.queryIntentActivities(sharingIntent, 0);
                    boolean isIntentSafe = activities.size() > 0;
                    if (isIntentSafe && mShareActionProvider != null) {
                        mShareActionProvider.setShareIntent(sharingIntent);
                    }
                }

                break;
            case R.id.menu_download:
                if (task != null) {
                    task.cancel(true);
                }

                if (!TextUtils.isEmpty(path)) {
                    new File(path).delete();
                }
                if (oriMenu.isVisible()) {
                    task = new PicSimpleBitmapWorkerTask();
                } else {
                    task = new PicSimpleBitmapWorkerTask();
                }
                task.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);

                break;
        }
        return super.onOptionsItemSelected(item);
    }

    class PicSaveTask extends MyAsyncTask<Void, Boolean, Boolean> {


        @Override
        protected Boolean doInBackground(Void... params) {
//            try {
//                MediaStore.Images.Media.insertImage(getContentResolver(), path, "", "");
//            } catch (FileNotFoundException e) {
//                AppLogger.e(e.getMessage());
//                cancel(true);
//            }
            return FileManager.saveToPicDir(path);
        }


        @Override
        protected void onPostExecute(Boolean value) {
            super.onPostExecute(value);
            saveTask = null;
            if (value)
                Toast.makeText(BrowserBigPicActivity.this, getString(R.string.save_to_album_successfully), Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(BrowserBigPicActivity.this, getString(R.string.cant_save_pic), Toast.LENGTH_SHORT).show();
        }


    }

    class PicSimpleBitmapWorkerTask extends MyAsyncTask<String, Integer, String> {

        FileDownloaderHttpHelper.DownloadListener downloadListener = new FileDownloaderHttpHelper.DownloadListener() {
            @Override
            public void pushProgress(int progress, int max) {
                publishProgress(progress, max);
            }


        };


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
//            pb.setIndeterminate(true);
            pb.setVisibility(View.VISIBLE);
            webView.setVisibility(View.INVISIBLE);
        }

        @Override
        protected String doInBackground(String... dd) {
            if (isCancelled()) {
                return null;
            }

            TaskCache.waitForMsgDetailPictureDownload(msg, downloadListener);

            String middlePath = FileManager.getFilePathFromUrl(msg.getBmiddle_pic(), FileLocationMethod.picture_bmiddle);
            String largePath = FileManager.getFilePathFromUrl(msg.getOriginal_pic(), FileLocationMethod.picture_large);
            if (new File(largePath).exists()) {
                return largePath;
            } else if (new File(middlePath).exists()) {
                return middlePath;
            } else {
                String data = msg.getBmiddle_pic();
                FileLocationMethod method = FileLocationMethod.picture_bmiddle;
                return ImageTool.getLargePictureWithoutRoundedCorner(data, downloadListener, method);

            }


        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            int progress = values[0];
            int max = values[1];
//            pb.setIndeterminate(false);
            pb.setMax(max);
            pb.setProgress(progress);
        }

        @Override
        protected void onCancelled(String bitmap) {
            if (bitmap != null) {


            }

            super.onCancelled(bitmap);
        }

        @Override
        protected void onPostExecute(final String bitmapPath) {

            if (!TextUtils.isEmpty(bitmapPath)) {
                path = bitmapPath;

                if (ImageTool.isThisBitmapCanRead(bitmapPath)) {
                    int[] size = ImageTool.getBitmapSize(bitmapPath);
                    getActionBar().setSubtitle(String.valueOf(size[0]) + "x" + String.valueOf(size[1]));
                }

                File file = new File(bitmapPath);

                String str1 = "file://" + file.getAbsolutePath().replace("/mnt/sdcard/", "/sdcard/");
                String str2 = "<html>\n<head>\n     <style>\n          html,body{background:transparent;margin:0;padding:0;}          *{-webkit-tap-highlight-color:rgba(0, 0, 0, 0);}\n     </style>\n     <script type=\"text/javascript\">\n     var imgUrl = \"" + str1 + "\";" + "     var objImage = new Image();\n" + "     var realWidth = 0;\n" + "     var realHeight = 0;\n" + "\n" + "     function onLoad() {\n" + "          objImage.onload = function() {\n" + "               realWidth = objImage.width;\n" + "               realHeight = objImage.height;\n" + "\n" + "               document.gagImg.src = imgUrl;\n" + "               onResize();\n" + "          }\n" + "          objImage.src = imgUrl;\n" + "     }\n" + "\n" + "     function onResize() {\n" + "          var scale = 1;\n" + "          var newWidth = document.gagImg.width;\n" + "          if (realWidth > newWidth) {\n" + "               scale = realWidth / newWidth;\n" + "          } else {\n" + "               scale = newWidth / realWidth;\n" + "          }\n" + "\n" + "          hiddenHeight = Math.ceil(30 * scale);\n" + "          document.getElementById('hiddenBar').style.height = hiddenHeight + \"px\";\n" + "          document.getElementById('hiddenBar').style.marginTop = -hiddenHeight + \"px\";\n" + "     }\n" + "     </script>\n" + "</head>\n" + "<body onload=\"onLoad()\" onresize=\"onResize()\" onclick=\"Android.toggleOverlayDisplay();\">\n" + "     <table style=\"width: 100%;height:100%;\">\n" + "          <tr style=\"width: 100%;\">\n" + "               <td valign=\"middle\" align=\"center\" style=\"width: 100%;\">\n" + "                    <div style=\"display:block\">\n" + "                         <img name=\"gagImg\" src=\"\" width=\"100%\" style=\"\" />\n" + "                    </div>\n" + "                    <div id=\"hiddenBar\" style=\"position:absolute; width: 100%; background: transparent;\"></div>\n" + "               </td>\n" + "          </tr>\n" + "     </table>\n" + "</body>\n" + "</html>";
                webView.loadDataWithBaseURL("file:///android_asset/", str2, "text/html", "utf-8", null);
                webView.setVisibility(View.VISIBLE);
                pb.setVisibility(View.INVISIBLE);

            } else {
                pb.setVisibility(View.GONE);
                int[] attrs = new int[]{R.attr.error};
                TypedArray ta = BrowserBigPicActivity.this.obtainStyledAttributes(attrs);
                Drawable drawableFromTheme = ta.getDrawable(0);
                //                webView.setImageDrawable(drawableFromTheme);
            }

        }


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Utility.cancelTasks(task, saveTask);
        webView.loadUrl("about:blank");
        webView.stopLoading();
    }
}
