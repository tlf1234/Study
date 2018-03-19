package com.cs.camerademo.camera1

import android.app.Activity
import android.graphics.ImageFormat
import android.hardware.Camera
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast

/**
 * author :  chensen
 * data  :  2018/3/17
 * desc :
 */
class CameraHelper : Camera.PreviewCallback {

    private var mCamera: Camera? = null
    private lateinit var mParameters: Camera.Parameters
    private var mSurfaceView: SurfaceView
    private var mSurfaceHolder: SurfaceHolder
    private var mActivity: Activity
    private var mCallBack: CallBack? = null
    var mCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK

    private var picWidth = 1080        //保存图片的宽
    private var picHeight = 1920       //保存图片的高
    private var surfaceViewWidth = 0   //预览区域的宽
    private var surfaceViewHeight = 0  //预览区域的高

    constructor(activity: Activity, surfaceView: SurfaceView) {
        mSurfaceView = surfaceView
        mSurfaceHolder = mSurfaceView.holder
        mActivity = activity
        init()
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        mCallBack?.onPreviewFrame(data)
    }

    fun takePic() {
        mCamera?.let {
            it.takePicture({}, null, { data, _ ->
                it.startPreview()
                mCallBack?.onTakePic(data)
            })
        }
    }

    private fun init() {
        mSurfaceHolder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                releaseCamera()
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                surfaceViewWidth = mSurfaceView.width
                surfaceViewHeight = mSurfaceView.height
                if (mCamera == null) {
                    openCamera(mCameraFacing)
                }
                startPreview()
            }
        })
    }

    //打开相机
    private fun openCamera(cameraFacing: Int = Camera.CameraInfo.CAMERA_FACING_BACK): Boolean {
        var supportCameraFacing = supportCameraFacing(cameraFacing)
        if (supportCameraFacing) {
            try {
                mCamera = Camera.open(cameraFacing)
                initParameters(mCamera!!)
                mCamera?.setPreviewCallback(this)
            } catch (e: Exception) {
                e.printStackTrace()
                toast("打开相机失败!")
                return false
            }
        }
        return supportCameraFacing
    }

    //配置相机参数
    private fun initParameters(camera: Camera) {
        try {
            mParameters = camera.parameters
            mParameters.previewFormat = ImageFormat.NV21

            //获取与指定宽高相等或最接近的尺寸
            //设置预览尺寸
            val bestPreviewSize = getBestSize(mSurfaceView.width, mSurfaceView.height, mParameters.supportedPreviewSizes)
            bestPreviewSize?.let {

                mParameters.setPreviewSize(it.width, it.height)
            }
            //设置保存图片尺寸
            val bestPicSize = getBestSize(picWidth, picHeight, mParameters.supportedPictureSizes)
            bestPicSize?.let {
                mParameters.setPictureSize(it.width, it.height)
            }
            //对焦模式
            if (isSupportFocus(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
                mParameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE

            camera.parameters = mParameters
        } catch (e: Exception) {
            e.printStackTrace()
            toast("相机初始化失败!")
        }
    }

    //开始预览
    fun startPreview() {
        mCamera?.let {
            it.setPreviewDisplay(mSurfaceHolder)
            setCameraDisplayOrientation(mActivity)
            it.startPreview()
            startFaceDetect()
        }
    }

    fun startFaceDetect() {
        mCamera?.let {
            it.startFaceDetection()
            it.setFaceDetectionListener { faces, _ ->
                mCallBack?.onFaceDetect(faces)
                log("检测到 ${faces.size} 张人脸")
            }
        }
    }

    fun stopFaceDetect() {
        mCamera?.let { it.stopFaceDetection() }
    }

    //判断是否支持某一对焦模式
    private fun isSupportFocus(focusMode: String): Boolean {
        var autoFocus = false
        val listFocusMode = mParameters.supportedFocusModes
        for (mode in listFocusMode) {
            if (mode == focusMode)
                autoFocus = true
            log("相机支持的对焦模式： " + mode)
        }
        return autoFocus
    }

    //切换摄像头
    fun exchangeCamera() {
        releaseCamera()
        mCameraFacing = if (mCameraFacing == Camera.CameraInfo.CAMERA_FACING_BACK)
            Camera.CameraInfo.CAMERA_FACING_FRONT
        else
            Camera.CameraInfo.CAMERA_FACING_BACK

        openCamera(mCameraFacing)
        startPreview()
    }

    //释放相机
    fun releaseCamera() {
        if (mCamera != null) {
            mCamera?.stopFaceDetection()
            mCamera?.stopPreview()
            mCamera?.setPreviewCallback(null)
            mCamera?.release()
            mCamera = null
        }
    }

    //获取与指定宽高相等或最接近的尺寸
    private fun getBestSize(targetWidth: Int, targetHeight: Int, sizeList: List<Camera.Size>): Camera.Size? {
        var bestSize: Camera.Size? = null
        var targetRatio = (targetHeight.toDouble() / targetWidth)  //目标大小的宽高比
        var minDiff = targetRatio

        for (size in sizeList) {
            if (size.width == targetHeight && size.height == targetWidth) {
                bestSize = size
                break
            }

            var supportedRatio = (size.width.toDouble() / size.height)
            if (Math.abs(supportedRatio - targetRatio) < minDiff) {
                minDiff = Math.abs(supportedRatio - targetRatio)
                bestSize = size
            }
            log("系统支持的尺寸 : ${size.width} * ${size.height} ,    比例$supportedRatio")
        }
        log("目标尺寸 ：$targetWidth * $targetHeight ，   比例  $targetRatio")
        log("最优尺寸 ：${bestSize?.height} * ${bestSize?.width}")
        return bestSize
    }

    //设置预览旋转的角度
    private fun setCameraDisplayOrientation(activity: Activity) {
        var info = Camera.CameraInfo()
        Camera.getCameraInfo(mCameraFacing, info)
        val rotation = activity.windowManager.defaultDisplay.rotation

        var screenDegree = 0
        when (rotation) {
            Surface.ROTATION_0 -> screenDegree = 0
            Surface.ROTATION_90 -> screenDegree = 90
            Surface.ROTATION_180 -> screenDegree = 180
            Surface.ROTATION_270 -> screenDegree = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + screenDegree) % 360
            result = (360 - result) % 360          // compensate the mirror
        } else {
            result = (info.orientation - screenDegree + 360) % 360
        }
        mCamera?.setDisplayOrientation(result)

        log("屏幕的旋转角度 : $rotation")
        log("setDisplayOrientation(result) : $result")
    }

    //判断是否支持某个相机
    private fun supportCameraFacing(cameraFacing: Int): Boolean {
        var info = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, info)
            if (info.facing == cameraFacing) return true
        }
        return false
    }


    private fun toast(msg: String) {
        Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT).show()
    }

    private fun log(msg: String) {
        Log.e("tag", msg)
    }

    fun addCallBack(callBack: CallBack) {
        this.mCallBack = callBack
    }

    interface CallBack {
        fun onPreviewFrame(data: ByteArray?)
        fun onTakePic(data: ByteArray?)
        fun onFaceDetect(faces: Array<Camera.Face>?)
    }
}