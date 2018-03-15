package com.example.uploadservice.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.uploadservice.R;
import com.example.uploadservice.adapter.PhotoListAdapter;
import com.example.uploadservice.util.permission.KbPermission;
import com.example.uploadservice.util.permission.KbPermissionListener;
import com.example.uploadservice.util.permission.KbPermissionUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PictureSelectActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "IconSelectActivity";
    public static final String PATH_IMAGE = "PATH_IMAGE";
    // 拍照
    private static final int PHOTO_REQUEST_TAKEPHOTO = 11;

    // 返回
    private FrameLayout mBack;
    // 预览
    private TextView mTvPreview;
    // 下一步
    private TextView mNext;
    // 图片列表
    private RecyclerView mPhotoList;
    // 预览图片
    private ImageView mIvPreview;

    private Context mContext;

    // 照片列表适配器
    private PhotoListAdapter mPhotoListAdapter;
    // 存放本地图片路径
    private List<String> mPhotoPathList = new ArrayList<>();

    // 保存图片的路径
    private File FILEPATH_FILE = Environment.getExternalStorageDirectory();
    // 图片名字
    private String imageName = "";

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1000:
                    for (String photoPath : mPhotoPathList) {
                        Log.e(TAG, "handleMessage: " + photoPath);
                    }
                    mPhotoListAdapter.addData(mPhotoPathList);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_select);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            SystemUtil.setLightStatusBar(this, Color.WHITE);
        }
        mContext = PictureSelectActivity.this;

        initView();
        getLocalPhoto();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mTvPreview.setTextColor(getResources().getColor(R.color.sub_title));
        mTvPreview.setClickable(false);
        mNext.setTextColor(getResources().getColor(R.color.sub_title));
        mNext.setClickable(false);
        mPhotoListAdapter.changeCheckState();
    }

    private void initView() {
        mBack = findViewById(R.id.iv_back);
        mBack.setOnClickListener(this);

        mTvPreview = (TextView) findViewById(R.id.tv_preview);
//        mTvPreview.setOnClickListener(this);

        mNext = (TextView) findViewById(R.id.tv_next);
//        mNext.setOnClickListener(this);

        mIvPreview = findViewById(R.id.iv_preview);
        mIvPreview.setOnClickListener(this);

        mPhotoList = (RecyclerView) findViewById(R.id.rv_photo_list);

        mPhotoList.setLayoutManager(new GridLayoutManager(this, 3));
        mPhotoListAdapter = new PhotoListAdapter(PictureSelectActivity.this);
        mPhotoList.setAdapter(mPhotoListAdapter);
        mPhotoListAdapter.setOnItemClickListener(new PhotoListAdapter.OnItemClickListener() {
            @Override
            public void onSelected(String photoPath) {
                Glide.with(mContext).load(photoPath).into(mIvPreview);
                mTvPreview.setTextColor(getResources().getColor(R.color.title));
                mTvPreview.setOnClickListener(PictureSelectActivity.this);
                mNext.setTextColor(getResources().getColor(R.color.title));
                mNext.setOnClickListener(PictureSelectActivity.this);
            }
        });

        mPhotoListAdapter.setOnTakePhotoListener(new PhotoListAdapter.OnTakePhotoListener() {
            @Override
            public void onTakePhoto() {
                mTvPreview.setClickable(false);
                mNext.setClickable(false);
                if (KbPermissionUtils.needRequestPermission()) {
                    KbPermission.with(PictureSelectActivity.this)
                            .requestCode(300)
                            .permission(Manifest.permission.CAMERA)
                            .callBack(new KbPermissionListener() {
                                @Override
                                public void onPermit(int requestCode, String... permission) {
                                    //调起相机
                                    takePhoto();
                                }

                                @Override
                                public void onCancel(int requestCode, String... permission) {
                                    KbPermissionUtils.goSetting(mContext);
                                }
                            })
                            .send();
                } else {
                    //调起相机
                    takePhoto();
                }
            }
        });
    }

    /**
     * 扫描本地图片
     */
    private void getLocalPhoto() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Uri mImageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver mContentResolver = mContext.getContentResolver();
                //只查询jpeg和png的图片  
                Cursor mCursor = mContentResolver.query(mImageUri, null,
                        MediaStore.Images.Media.MIME_TYPE + "=? or "
                                + MediaStore.Images.Media.MIME_TYPE + "=?",
                        new String[]{"image/jpeg", "image/png"}, MediaStore.Images.Media.DATE_MODIFIED);
                if (mCursor == null) {
                    return;
                }
                while (mCursor.moveToNext()) {
                    //获取图片的路径  
                    String path = mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    if (!TextUtils.isEmpty(path)) {
                        mPhotoPathList.add(path);
                    }
                }
                mHandler.sendEmptyMessage(1000);
                mCursor.close();
            }
        }).start();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_back:
                finish();
                break;
            case R.id.tv_preview:
                mIvPreview.setVisibility(View.VISIBLE);
                break;
            case R.id.iv_preview:
                mIvPreview.setVisibility(View.GONE);
                break;
            case R.id.tv_next:


                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == 0) return;
        // 拍照回调
        if (requestCode == PHOTO_REQUEST_TAKEPHOTO) {

        }
    }

    /**
     * 拍照
     */
    private void takePhoto() {

        if (!FILEPATH_FILE.exists()) {
            FILEPATH_FILE.mkdirs();
        }
        imageName = getNowTime() + ".png";
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 指定调用相机拍照后照片的储存路径
        File file = new File(FILEPATH_FILE, imageName);
        Log.e(TAG, "takePhoto: " + file.getAbsolutePath());
        intent.putExtra(MediaStore.EXTRA_OUTPUT, getUriForFile(mContext, file));

        startActivityForResult(intent, PHOTO_REQUEST_TAKEPHOTO);
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_bottom);
    }

    private static Uri getUriForFile(Context context, File file) {
        if (context == null || file == null) {
            throw new NullPointerException();
        }
        Uri uri;
        /**
         * 需要在清单文件的application节点中加入以下代码
         * <provider
         android:name="android.support.v4.content.FileProvider"
         android:authorities="com.example.uploadservice.fileprovider"
         android:exported="false"
         android:grantUriPermissions="true">
         <meta-data
         android:name="android.support.FILE_PROVIDER_PATHS"
         android:resource="@xml/filepaths" />
         </provider>
         */
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(context.getApplicationContext(), "com.example.uploadservice.fileprovider", file);
        } else {
            uri = Uri.fromFile(file);
        }
        return uri;
    }

    /**
     * 获取当前时间
     *
     * @return
     */
    private String getNowTime() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMddHHmmssSS");
        return dateFormat.format(date);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        KbPermission.onRequestPermissionResult(requestCode, permissions, grantResults);
    }
}