package demo.d3200.integration

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File
import org.json.JSONObject

/** Blocking call into the existing harmonica_eval Host -> Core/Ports -> Algorithms chain. */
object AnalysisFacade {
    @Synchronized
    private fun ensurePython(context: Context) {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
    }

    fun analyze(context: Context, reference: File, practice: File): JSONObject {
        require(reference.isFile && practice.isFile)
        ensurePython(context)
        val json = Python.getInstance().getModule("mobile_bridge")
            .callAttr("analyze", reference.absolutePath, practice.absolutePath)
            .toString()
        return JSONObject(json)
    }
}
