package com.bringyour.network.goclient.support

import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.bringyour.network.goclient.vc.GLViewCallback
import com.bringyour.network.goclient.vc.GLViewController
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLSurfaceViewBinder (name: String, view: GLSurfaceView, glVc: GLViewController) :
    GLViewCallback, GLSurfaceView.Renderer, View.OnAttachStateChangeListener {

    val name = name
    val view = view
    val glVc = glVc

//    val mainHandler = Handler(Looper.getMainLooper());


    companion object {
        fun bind(name: String, view: GLSurfaceView, glVc: GLViewController) {
            val glBinder = GLSurfaceViewBinder(name, view, glVc)
            // GLES20

            view.setEGLContextClientVersion(2)
//            view.setZOrderOnTop(true)
//            view.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            view.holder.setFormat(PixelFormat.OPAQUE)

            // fixme
//            view.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

            view.setRenderer(glBinder)
            view.renderMode = RENDERMODE_WHEN_DIRTY


            view.addOnAttachStateChangeListener(glBinder)
        }
    }


    // GLViewCallback

    override fun update() {
        val isMain = Looper.myLooper() == Looper.getMainLooper()
//        Log.d(name, String.format("update (main=%b)", isMain))
        view.requestRender()
    }


    // GLSurfaceView.Renderer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glVc.surfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glVc.surfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
//        Log.d(name, "draw enter")
        glVc.drawFrame()
//        Log.d(name, "draw exit")
    }


    // View.OnAttachStateChangeListener

    override fun onViewAttachedToWindow(v: View) {
        Log.d(name, "attach enter")
        glVc.start(this)
        Log.d(name, "attach exit")
    }

    override fun onViewDetachedFromWindow(v: View) {
        Log.d(name, "detach enter")
        glVc.stop()
        Log.d(name, "detach exit")
    }
}