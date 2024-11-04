import java.io.File
import java.time.LocalDateTime

interface Observer{
    fun update(message : String?)
}

class DefaultLogger(private val loggerTag : String = "DefaultLog"): Observer{
    override fun update(message: String?) {
        println("$loggerTag :$message")
    }
}
class FileLogger(private val loggerFolderName : String = "logs" , private val loggerFileName : String = "Logs",val extension : String="txt"): Observer{
    private val fileName = "$loggerFolderName/$loggerFileName(${LocalDateTime.now().toString().replace('-','_').replace(':','_')}).$extension"
    private val dir = File(loggerFolderName)
    private val dirResult = dir.mkdir()
    private val file = File(fileName)

    override fun update(message: String?) {
        file.appendText(message+"("+ LocalDateTime.now().toString()+")"+ "\n")
    }
}