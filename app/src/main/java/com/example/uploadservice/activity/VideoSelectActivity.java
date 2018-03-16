package com.example.uploadservice.activity;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.uploadservice.R;
import com.example.uploadservice.adapter.VideoListAdapter;
import com.example.uploadservice.model.Topic;
import com.example.uploadservice.util.SizeUtils;
import com.example.uploadservice.util.SystemUtil;
import com.example.uploadservice.util.VideoFileUtils;
import com.example.uploadservice.util.permission.KbPermission;
import com.example.uploadservice.util.permission.KbPermissionListener;
import com.example.uploadservice.util.permission.KbPermissionUtils;
import com.example.uploadservice.view.SquareRelativeLayout;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoSelectActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "VideoRecordActivity";
    private static final int MSG_SHOW_VIDEO_LIST = 1000;
    private static final int MSG_HIDE_PLAY_PAUS_VIEW = 2000;
    private static final int MSG_DELAY_FIRST_FRAME = 3000;
    //是否已显示过该活动的规则
    private static final String KEY_HAS_SHOWED_VIDEO_RECORD_RULES_PREFIX = "KEY_HAS_SHOWED_VIDEO_RECORD_RULES_PREFIX";

    public static final int STATE_IDLE = 0; //通常状态
    public static final int STATE_PLAYING = 1; //视频正在播放
    public static final int STATE_PAUSED = 2; //视频暂停
    public static final int DEFAULT_SHOW_TIME = 3000; // 控制器的默认显示时间3秒

    private TextView mTitleNext; // 下一步
    private TextView mTvCancel; // 取消
    private SquareRelativeLayout mVideoPlay; //承载播放器的布局
    private TextureView mTextureview; //更换为TextureView
    private Surface mSurface;
    private RecyclerView mVideoList; // 视频列表
    private ImageView mPlayPause; // 播放暂停按钮
    private ImageView mVideoBg; // 视频缩略图
    private ImageView mIvEmpty; //占位图
    private TextView mTvEmpty; //空白时文案
    private FrameLayout mLoading; // 加载中

    private LinearLayout mFileSelect; // 文件选择按钮
    private RelativeLayout mFileContent; // 文件选择布局
    private RecyclerView mFileList; // 文件选择列表

    public List<Topic> mAllVideoList = new ArrayList<>(); // 视频信息集合
    private VideoListAdapter mVideoAdapter; // 视频列表适配器
    private MediaPlayer mMediaPlayer = new MediaPlayer(); // 播放器

    private Context mContext;

    private int mCurState = STATE_IDLE; // 当前状态
    private boolean mFileSelectIsOpen = false; // 是否打开文件选择
    private boolean mPlayPuseIsShow = false; // 是否显示了播放暂停
    private boolean mIsPermission; //是否授予了权限

    @SuppressLint("HandlerLeak")
    public Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_SHOW_VIDEO_LIST) {
                if (VideoSelectActivity.this.isFinishing()) {
                    return;
                }
                mLoading.setVisibility(View.GONE);
                if (mAllVideoList.size() <= 0) {
                    return;
                }

                mIvEmpty.setVisibility(View.GONE);
                mTvEmpty.setVisibility(View.GONE);

                addSurfaceView(mVideoPlay, mAllVideoList.get(0));
                Glide.with(mContext).load(new File(mAllVideoList.get(0).getLocalVideoPath())).into(mVideoBg);
                mVideoAdapter.addData(mAllVideoList);
            } else if (msg.what == MSG_HIDE_PLAY_PAUS_VIEW) {
                if (mMediaPlayer == null) return;
                if (mMediaPlayer.isPlaying()) {
                    mPlayPause.setVisibility(View.GONE);
                    mPlayPuseIsShow = false;
                } else {
                    mPlayPause.setVisibility(View.VISIBLE);
                    mPlayPuseIsShow = true;
                }
            } else if (msg.what == MSG_DELAY_FIRST_FRAME) {
                mVideoBg.setVisibility(View.GONE);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_select);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SystemUtil.setLightStatusBar(this, Color.WHITE);
        }
        Log.e(TAG, "onCreate: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        mContext = VideoSelectActivity.this;

        initView();

        getData();

        initVideo();

        setListener();

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mCurState = STATE_IDLE;
            mHandler.removeMessages(MSG_HIDE_PLAY_PAUS_VIEW);
            if (mPlayPause != null) {
                mPlayPause.setImageResource(R.drawable.img_play);
                mPlayPause.setVisibility(View.VISIBLE);
            }
            if (mVideoBg != null)
                mVideoBg.setVisibility(View.VISIBLE);
        }
    }

    /**
     *
     */
    private void initView() {
        mIvEmpty = findViewById(R.id.iv_empty);
        mTvEmpty = findViewById(R.id.tv_empty);

        mTitleNext = (TextView) findViewById(R.id.tv_next);
        mTvCancel = (TextView) findViewById(R.id.tv_cancel);

        mVideoPlay = findViewById(R.id.rl_video_play);
//        addSurfaceView(mVideoPlay);

        mVideoList = (RecyclerView) findViewById(R.id.rv_video_list);
        mVideoList.setLayoutManager(new GridLayoutManager(this, 3));
        mVideoAdapter = new VideoListAdapter(mContext);
        mVideoList.setAdapter(mVideoAdapter);

        // 最近添加按钮
        mFileSelect = (LinearLayout) findViewById(R.id.ll_new_add);
        // 最近添加布局
        mFileContent = (RelativeLayout) findViewById(R.id.rl_new_add_content);
        // 最近添加列表
        mFileList = (RecyclerView) findViewById(R.id.rv_new_add);
        mFileList.setLayoutManager(new LinearLayoutManager(mContext));

        mLoading = findViewById(R.id.fl_loading);

    }

    /**
     * 配置视频播放相关
     */
    private void initVideo() {

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                // 默认背景隐藏，播放按钮隐藏
                //mVideoBg.setVisibility(View.GONE);
                mPlayPause.setImageResource(R.drawable.img_pause);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPuseIsShow = true;
                Log.e(TAG, "onPrepared: 播放");
                mMediaPlayer.start();
                mCurState = STATE_PLAYING;
                //延迟隐藏第一帧图片
                mHandler.removeMessages(MSG_DELAY_FIRST_FRAME);
                mHandler.sendEmptyMessageDelayed(MSG_DELAY_FIRST_FRAME, 300);
                //三秒消失播放按钮
                mHandler.removeMessages(MSG_HIDE_PLAY_PAUS_VIEW);
                mHandler.sendEmptyMessageDelayed(MSG_HIDE_PLAY_PAUS_VIEW, DEFAULT_SHOW_TIME);
            }
        });

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                deleteSurfaceView(mVideoPlay);
                addSurfaceView(mVideoPlay, mVideoAdapter.getCheckPosition());
                initVideo();
//                mVideoBg.setImageBitmap(mVideoAdapter.getCheckPosition().getThumb());
                if (mVideoAdapter.getCheckPosition() != null) {
                    Glide.with(mContext).load(new File(mVideoAdapter.getCheckPosition().getLocalVideoPath())).into(mVideoBg);
                }
                // 移除已发送的消息
                mHandler.removeMessages(MSG_HIDE_PLAY_PAUS_VIEW);
                mVideoBg.setVisibility(View.VISIBLE);
                mPlayPause.setImageResource(R.drawable.img_play);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPuseIsShow = true;
                mCurState = STATE_IDLE;
            }
        });
    }

    /**
     * 设置监听
     */
    private void setListener() {
        mVideoAdapter.setOnItemClickListener(new VideoListAdapter.OnItemClickListener() {
            @Override
            public void onSelected(Topic videoItem) {

                if (videoItem == mVideoAdapter.getCheckPosition())
                    return;
                if (mMediaPlayer != null) {
                    mMediaPlayer.stop();
                    mMediaPlayer.reset();

                    mCurState = STATE_IDLE;

                    deleteSurfaceView(mVideoPlay);
                    addSurfaceView(mVideoPlay, videoItem);
                    initVideo();
                }
                Glide.with(mContext).load(videoItem.getLocalVideoPath()).into(mVideoBg);
                // 默认背景显示，播放按钮显示
                mVideoBg.setVisibility(View.VISIBLE);
                mPlayPause.setImageResource(R.drawable.img_play);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPuseIsShow = true;
            }
        });

        mVideoAdapter.setOnVideoRecordListener(new VideoListAdapter.OnVideoRecordListener() {
            @Override
            public void onVideoRecord() {
            }
        });

        mFileSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mFileSelectIsOpen) {
                    mFileSelectIsOpen = false;
                    mTitleNext.setVisibility(View.VISIBLE);
//                    iv_new_add.setImageResource(R.mipmap.ic_keyboard_arrow_down);
                    AnimatorSet set = new AnimatorSet();
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mFileContent.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
                    set.playTogether(ObjectAnimator.ofFloat(mFileList, "translationY", 0, getWindowManager().getDefaultDisplay().getHeight()),
                            ObjectAnimator.ofFloat(mFileList, "alpha", 1, 0));
                    set.setDuration(400);
                    set.start();
                } else {
                    mFileSelectIsOpen = true;
                    mTitleNext.setVisibility(View.GONE);
//                    iv_new_add.setImageResource(R.mipmap.ic_keyboard_arrow_up);
                    mFileContent.setVisibility(View.VISIBLE);

                    AnimatorSet set = new AnimatorSet();
                    set.playTogether(ObjectAnimator.ofFloat(mFileList, "translationY", getWindowManager().getDefaultDisplay().getHeight(), 0),
                            ObjectAnimator.ofFloat(mFileList, "alpha", 0, 1));
                    set.setDuration(400);
                    set.start();
                }
            }
        });

        mTvCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFileSelectIsOpen) {
                    mFileSelectIsOpen = false;
                    mTitleNext.setVisibility(View.VISIBLE);
//                    iv_new_add.setImageResource(R.mipmap.ic_keyboard_arrow_down);
                    AnimatorSet set = new AnimatorSet();
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mFileContent.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });
                    set.playTogether(ObjectAnimator.ofFloat(mFileList, "translationY", 0, getWindowManager().getDefaultDisplay().getHeight()),
                            ObjectAnimator.ofFloat(mFileList, "alpha", 1, 0));
                    set.setDuration(400);
                    set.start();
                } else {
                    finish();
                }
            }
        });

        mVideoPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMediaPlayer.isPlaying() && mPlayPuseIsShow == true) {
                    mPlayPause.setVisibility(View.GONE);
                    mPlayPuseIsShow = false;
                } else if (mMediaPlayer.isPlaying() && mPlayPuseIsShow == false) {
                    mPlayPause.setVisibility(View.VISIBLE);
                    mPlayPuseIsShow = true;
                    mHandler.removeMessages(MSG_HIDE_PLAY_PAUS_VIEW);
                    mHandler.sendEmptyMessageDelayed(MSG_HIDE_PLAY_PAUS_VIEW, DEFAULT_SHOW_TIME);
                }
            }
        });

        mTitleNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mVideoAdapter.getCheckPosition() != null) {

                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                        mCurState = STATE_PAUSED;
                        mPlayPause.setImageResource(R.drawable.img_play);
                        mPlayPause.setVisibility(View.VISIBLE);
                        mPlayPuseIsShow = true;
                    }
                }
            }
        });
    }


    /**
     * 遍历  获取视频资源
     */
    private void getData() {
        mLoading.setVisibility(View.VISIBLE);
        // 获取本地相册内视频文件
        new Thread() {
            @Override
            public void run() {
                VideoFileUtils.getVideoFile(mAllVideoList, new File(Environment.getExternalStorageDirectory() /*+ "/DCIM"*/ + "/DCIM/Camera"));
//                VideoFileUtils.getVideoFile(mAllVideoList, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath()));
                if (mContext != null) {
                    mHandler.sendEmptyMessage(MSG_SHOW_VIDEO_LIST);
                }
            }
        }.start();
    }

    /**
     * 添加SurfaceView
     */
    private void addSurfaceView(RelativeLayout relativeLayout, Topic videoItem) {

        //mSurface = new SurfaceView(this);
        mTextureview = new TextureView(this);
        mTextureview.setSurfaceTextureListener(this);//设置监听函数  重写4个方法

        RelativeLayout.LayoutParams lp1 = null;
        // 特殊处理阿里云拍摄的视频
//        if (videoItem.getLocalVideoPath().contains("DaishuAli")) {
//            videoItem.setRotation("90");
//        }
        try {
            //先判断旋转方向再判断视频宽高度，确保适用于大多数视频
            if (videoItem == null) {
                lp1 = new RelativeLayout.LayoutParams(
                        SizeUtils.dp2px(mContext, 211), ViewGroup.LayoutParams.MATCH_PARENT
                );
            } else if (Integer.parseInt(videoItem.getRotation()) == 0 || Integer.parseInt(videoItem.getRotation()) == 180) {
                if (Integer.parseInt(videoItem.getHeight()) > Integer.parseInt(videoItem.getWidth())) {
                    lp1 = new RelativeLayout.LayoutParams(
                            SizeUtils.dp2px(mContext, 211), ViewGroup.LayoutParams.MATCH_PARENT
                    );
                } else if (Integer.parseInt(videoItem.getHeight()) == Integer.parseInt(videoItem.getWidth())) {
                    lp1 = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    );
                } else {
                    lp1 = new RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, SizeUtils.dp2px(mContext, 202)
                    );
                }
            } else if (Integer.parseInt(videoItem.getRotation()) == 90) {
                lp1 = new RelativeLayout.LayoutParams(
                        SizeUtils.dp2px(mContext, 211), ViewGroup.LayoutParams.MATCH_PARENT
                );
            } else if (Integer.parseInt(videoItem.getRotation()) == 270) {
                lp1 = new RelativeLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, SizeUtils.dp2px(mContext, 202)
                );
            } else {
                lp1 = new RelativeLayout.LayoutParams(
                        SizeUtils.dp2px(mContext, 211), ViewGroup.LayoutParams.MATCH_PARENT
                );
            }
            lp1.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

            relativeLayout.addView(mTextureview, lp1);

            mVideoBg = new ImageView(this);
            mVideoBg.setScaleType(ImageView.ScaleType.FIT_XY);
            relativeLayout.addView(mVideoBg, lp1);

            mPlayPause = new ImageView(this);
            mPlayPause.setImageResource(R.drawable.img_play);
            RelativeLayout.LayoutParams lp2 = new RelativeLayout.LayoutParams(
                    SizeUtils.dp2px(mContext, 30), SizeUtils.dp2px(mContext, 36)
            );

            mPlayPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mVideoAdapter.getCheckPosition() == null) {
                        Log.e(TAG, "onClick: " + mVideoAdapter.getCheckPosition().getLocalVideoPath());
                        return;
                    }
                    Log.e(TAG, "onClick: " + mCurState);
                    switch (mCurState) {

                        case STATE_PLAYING:
                            mMediaPlayer.pause();
                            mCurState = STATE_PAUSED;
                            mPlayPause.setImageResource(R.drawable.img_play);
                            break;
                        case STATE_PAUSED:
                            mMediaPlayer.start();
                            mCurState = STATE_PLAYING;
                            mPlayPause.setImageResource(R.drawable.img_pause);
                            mHandler.removeMessages(MSG_HIDE_PLAY_PAUS_VIEW);
                            mHandler.sendEmptyMessageDelayed(MSG_HIDE_PLAY_PAUS_VIEW, DEFAULT_SHOW_TIME);
                            break;
                        case STATE_IDLE:
                            try {
                                mMediaPlayer.reset();
                                Log.e(TAG, "onClick: STATE_IDLE");
                                mMediaPlayer.setSurface(mSurface);
                                mMediaPlayer.setDataSource(mVideoAdapter.getCheckPosition().getLocalVideoPath());
                                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                                mMediaPlayer.setScreenOnWhilePlaying(true);
                                mMediaPlayer.prepareAsync();

                            } catch (Exception e) {
                                Toast.makeText(VideoSelectActivity.this, "该视频无法播放，换一个吧~", Toast.LENGTH_SHORT).show();
                            }
                            break;
                    }
                }
            });

            lp2.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            relativeLayout.addView(mPlayPause, lp2);
        } catch (Exception e) {
            lp1 = new RelativeLayout.LayoutParams(
                    SizeUtils.dp2px(mContext, 211), ViewGroup.LayoutParams.MATCH_PARENT
            );
            lp1.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

            mVideoBg = new ImageView(this);
            mVideoBg.setBackgroundColor(getResources().getColor(R.color.black));
            relativeLayout.addView(mVideoBg, lp1);

            mPlayPause = new ImageView(this);
            mPlayPause.setImageResource(R.drawable.img_play);
            RelativeLayout.LayoutParams lp2 = new RelativeLayout.LayoutParams(
                    SizeUtils.dp2px(mContext, 30), SizeUtils.dp2px(mContext, 36)
            );

            mPlayPause.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(mContext, "该视频无法播放，换一个吧~", Toast.LENGTH_SHORT).show();
                }
            });
            mVideoBg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(mContext, "该视频无法播放，换一个吧~", Toast.LENGTH_SHORT).show();
                }
            });

            lp2.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            relativeLayout.addView(mPlayPause, lp2);
        }
    }


    /**
     * 删除SurfaceView
     *
     * @param relativeLayout
     */
    private void deleteSurfaceView(RelativeLayout relativeLayout) {
        relativeLayout.removeAllViews();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        KbPermission.onRequestPermissionResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mSurface = new Surface(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void finish() {
        super.finish();
        //Activity退出时动画
        overridePendingTransition(R.anim.slide_out_bottom, R.anim.slide_out_top);
    }
}