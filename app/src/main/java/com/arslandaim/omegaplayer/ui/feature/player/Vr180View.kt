package com.arslandaim.omegaplayer.ui.feature.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.*
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.runtime.*
import androidx.media3.common.Player
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

internal class Vr180State {
    var enabled by mutableStateOf(false)
    var rightEye by mutableStateOf(false)
    var motion by mutableStateOf(false)
    var recenter by mutableIntStateOf(0)
    @Volatile var yaw = 0f
    @Volatile var pitch = 0f
    fun look(dx: Float, dy: Float) {
        yaw = (yaw + dx).coerceIn(-89f, 89f)
        pitch = (pitch + dy).coerceIn(-85f, 85f)
    }
    fun center() { yaw = 0f; pitch = 0f; recenter++ }
}

/** Standard equirectangular VR180 SBS: one eye occupies half the source width. */
internal fun vr180Uv(yaw: Float, pitch: Float, rightEye: Boolean): Pair<Float, Float>? {
    if (abs(yaw) > 90f || abs(pitch) > 90f) return null
    return ((yaw / 180f + .5f) * .5f + if (rightEye) .5f else 0f) to (pitch / 180f + .5f)
}

/** One decoded video texture, projected onto a 180-degree hemisphere, never a second player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class Vr180View(context: Context, private val state: Vr180State) : GLSurfaceView(context),
    GLSurfaceView.Renderer, SensorEventListener {
    private val main = Handler(Looper.getMainLooper())
    private val sensors = context.getSystemService(SensorManager::class.java)
    val motionAvailable: Boolean get() = rotationSensor != null
    private val rotationSensor = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var player: Player? = null
    private var output: Surface? = null
    private var texture: SurfaceTexture? = null
    @Volatile private var closed = false
    @Volatile private var framePending = false
    @Volatile private var eye = false
    @Volatile private var sensorYaw = 0f
    @Volatile private var sensorPitch = 0f
    private var base: FloatArray? = null
    private var centerRevision = -1
    private var program = 0
    private var textureId = 0
    private var aspect = 1f
    private val transform = FloatArray(16)
    private val vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)); position(0) }
    init { setEGLContextClientVersion(2); preserveEGLContextOnPause = true; setRenderer(this); renderMode = RENDERMODE_CONTINUOUSLY }

    fun bind(value: Player?) {
        if (player !== value) {
            output?.let { player?.clearVideoSurface(it) }
            player = value
            output?.let { value?.setVideoSurface(it) }
        }
        eye = state.rightEye
        sensors.unregisterListener(this)
        if (state.motion && motionAvailable && !closed) sensors.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        else { sensorYaw = 0f; sensorPitch = 0f; base = null }
    }
    override fun onPause() { sensors.unregisterListener(this); super.onPause() }
    override fun onResume() { super.onResume(); bind(player) }
    fun close() {
        closed = true
        sensors.unregisterListener(this)
        output?.let { player?.clearVideoSurface(it); it.release() }
        output = null; player = null
        queueEvent { texture?.release(); texture = null }
        onPause()
    }
    override fun onSensorChanged(event: SensorEvent) {
        val matrix = FloatArray(16)
        val mapped = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val axes = when(rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, mapped)
        val angles = SensorManager.getOrientation(mapped, FloatArray(3))
        if (base == null || centerRevision != state.recenter) {
            base = angles.copyOf(); centerRevision = state.recenter
        }
        fun delta(a: Float, b: Float): Float = ((Math.toDegrees((a-b).toDouble()).toFloat() + 540f) % 360f) - 180f
        sensorYaw = delta(angles[0], base!![0])
        sensorPitch = -delta(angles[1], base!![1])
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (closed) return
        fun shader(kind: Int, text: String): Int = glCreateShader(kind).also {
            glShaderSource(it, text); glCompileShader(it)
            val ok = IntArray(1); glGetShaderiv(it, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { glGetShaderInfoLog(it) }
        }
        val vs = shader(GL_VERTEX_SHADER, "attribute vec2 a; varying vec2 p; void main(){p=a;gl_Position=vec4(a,0.,1.);}")
        val fs = shader(GL_FRAGMENT_SHADER, """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            uniform samplerExternalOES video;
            uniform mat4 texMatrix;
            uniform float aspect, yaw, pitch, eye;
            varying vec2 p;
            void main(){
                vec3 d=normalize(vec3(p.x*aspect*.7,p.y*.7,-1.));
                float cp=cos(pitch),sp=sin(pitch),cy=cos(yaw),sy=sin(yaw);
                d=vec3(d.x,cp*d.y-sp*d.z,sp*d.y+cp*d.z);
                d=vec3(cy*d.x-sy*d.z,d.y,sy*d.x+cy*d.z);
                float lon=atan(d.x,-d.z);
                if(abs(lon)>1.57079633){gl_FragColor=vec4(0.,0.,0.,1.);return;}
                vec2 uv=vec2((lon/3.14159265+.5)*.5+eye*.5,asin(clamp(d.y,-1.,1.))/3.14159265+.5);
                gl_FragColor=texture2D(video,(texMatrix*vec4(uv,0.,1.)).xy);
            }
        """.trimIndent())
        program=glCreateProgram(); glAttachShader(program,vs); glAttachShader(program,fs); glLinkProgram(program)
        val ok=IntArray(1); glGetProgramiv(program,GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) { glGetProgramInfoLog(program) }
        glDeleteShader(vs); glDeleteShader(fs)
        val ids=IntArray(1); glGenTextures(1,ids,0); textureId=ids[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MIN_FILTER,GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        texture?.release()
        val next=SurfaceTexture(textureId).also { it.setOnFrameAvailableListener { framePending=true } }
        texture=next
        val surface=Surface(next)
        main.post {
            if (closed) surface.release()
            else { output?.let { player?.clearVideoSurface(it); it.release() }; output=surface; player?.setVideoSurface(surface) }
        }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0,0,width,height); aspect=width.toFloat()/height.coerceAtLeast(1)
    }
    override fun onDrawFrame(gl: GL10?) {
        glClearColor(0f,0f,0f,1f); glClear(GL_COLOR_BUFFER_BIT)
        val source=texture ?: return
        if (closed) return
        if(framePending){framePending=false; source.updateTexImage(); source.getTransformMatrix(transform)}
        glUseProgram(program)
        glActiveTexture(GL_TEXTURE0); glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId)
        glUniform1i(glGetUniformLocation(program,"video"),0)
        glUniformMatrix4fv(glGetUniformLocation(program,"texMatrix"),1,false,transform,0)
        glUniform1f(glGetUniformLocation(program,"aspect"),aspect)
        glUniform1f(glGetUniformLocation(program,"yaw"),Math.toRadians((state.yaw+sensorYaw).coerceIn(-89f,89f).toDouble()).toFloat())
        glUniform1f(glGetUniformLocation(program,"pitch"),Math.toRadians((state.pitch+sensorPitch).coerceIn(-85f,85f).toDouble()).toFloat())
        glUniform1f(glGetUniformLocation(program,"eye"),if(eye)1f else 0f)
        val a=glGetAttribLocation(program,"a"); glEnableVertexAttribArray(a)
        glVertexAttribPointer(a,2,GL_FLOAT,false,0,vertices); glDrawArrays(GL_TRIANGLE_STRIP,0,4)
    }
}
