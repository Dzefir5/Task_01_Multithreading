import java.io.File
import java.sql.Time
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.swing.text.StyledEditorKit.BoldAction
import kotlin.math.nextDown


fun main() {
    val fileLogger = FileLogger("logs","MyLogs")
    val logger = DefaultLogger("MyLog")
    val bank = Bank()
    bank.addObserver(fileLogger)
    val client1 = ClientFactory(bank).createClient(1)
    val client2 = ClientFactory(bank).createClient(2)
    bank.addClient(client1,client2)
    bank.addTransaction(Transaction.Deposit(1,"USD",1000.0))
    bank.addTransaction(Transaction.Deposit(2,"USD",1000.0))

    repeat(5000){
        val transaction1 = Transaction.Deposit(1,"USD",1.0)
        val transaction2 = Transaction.Withdraw(1,"USD",1.0)
        bank.addTransaction(transaction1)
        bank.addTransaction(transaction2)
    }
    println(client1.clientCurrency["USD"])
    bank.awaitCashiers()
    println(client1.clientCurrency["USD"])
    repeat(25){
        bank.addTransaction(Transaction.TransferFunds(1,2,"USD",10.0))
        bank.addTransaction(Transaction.TransferFunds(2,1,"USD",10.0))
        println(client1.clientCurrency["USD"])
    }
    bank.awaitCloseCashiers()
    println(client1.clientCurrency["USD"])

}