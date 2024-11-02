import java.io.File
import java.sql.Time
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.nextDown

val RUB_DEF = 130.0
val USD_DEF = 1.0
val EUR_DEF = 1.29
class ClientFactory(private val bank:Bank){
    fun createClient(id: Int) : Client{
        val client = Client(id ,ConcurrentHashMap<String, Double>())
        bank.exchangeRates.forEach{ (currency, _) ->
            client.clientCurrency[currency]=0.0
        }
        return client
    }
}

enum class CashierState{
    RUNNING, STOPPED
}
class Client(
    val id: Int,
    var clientCurrency : ConcurrentHashMap<String, Double>
)

open class BankException():Exception()
class ClientNotFoundException(val clientId : Int) : BankException()
class InvalidTransactionException() : BankException()
class InsufficientTransactionException(val clientId : Int) : BankException()


sealed class Transaction{
    data class Deposit(val clientId : Int,val currencyKey : String, val amount : Double):Transaction()
    data class Withdraw(val clientId : Int,val currencyKey : String, val amount : Double):Transaction()
    data class ExchangeCurrency(val clientId : Int,val fromCurrencyKey : String,val toCurrencyKey : String, val amount : Double):Transaction()
    data class TransferFunds(val fromClientId : Int,val toClientId : Int,val currencyKey : String, val amount : Double):Transaction()
}

interface Observer{
    fun update(message : String?)
}
class Logger(private val loggerTag : String = "DefaultLog"): Observer{
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
        println(Thread.currentThread())
        file.appendText(message+"("+LocalDateTime.now().toString()+")"+ "\n")
    }
}


class Bank {
    private val observers = mutableListOf<Observer>()
    val clients = ConcurrentHashMap<Int,Client>()
    val exchangeRates = ConcurrentHashMap<String, Double>()
    val transactionQueue = LinkedBlockingQueue<Transaction>()
    val cashiers = ArrayList<Cashier>()
    val scheduledExecutor = ScheduledThreadPoolExecutor(1)
    init {
        startCashiers(5)
        initExchangeRate()
    }

    private fun initExchangeRate(){
        addExchangeRate("USD",USD_DEF)
        addExchangeRate("RUB",RUB_DEF)
        addExchangeRate("EUR",EUR_DEF)
        scheduledExecutor.scheduleAtFixedRate({
            updateExchangeRate(5.0)
        }, 0, 1, TimeUnit.MINUTES)
    }
    fun addObserver(observer: Observer) {
        observers.add(observer)
    }
    fun addObserver(observerCollection: Collection<Observer>) {
        observerCollection.forEach(){observer ->
            observers.add(observer)
        }
    }
    fun addObserver(vararg observerList: Observer) {
        observerList.forEach(){observer ->
            observers.add(observer)
        }
    }

    fun notifyObservers(message: String?) {
        observers.forEach {
            it.update(message)
        }
    }

    fun addClient(client: Client) {
        clients[client.id] = client
        notifyObservers("Added client with clientId : ${client.id}")
    }
    fun addClient(clientCollection: Collection<Client>) {
        clientCollection.forEach(){client ->
            clients[client.id] = client
            notifyObservers("Added client with clientId : ${client.id}")
        }
    }
    fun addClient(vararg clientsList: Client) {
        clientsList.forEach(){client ->
            clients[client.id] = client
            notifyObservers("Added client with clientId : ${client.id}")
        }
    }


    fun addTransaction(transaction: Transaction) {
        transactionQueue.add(transaction)
    }
    fun addTransaction(transactionCollection: Collection<Transaction>) {
        transactionCollection.forEach(){transaction ->
            transactionQueue.add(transaction)
        }
    }
    fun addTransaction(vararg transactionList : Transaction) {
        transactionList.forEach(){transaction ->
            transactionQueue.add(transaction)
        }
    }

    private fun updateExchangeRate(power: Double = 5.0){
        val factor =1.0 + (0.5 - Math.random())/ power
        exchangeRates.forEach(){ (key,value)->
           exchangeRates[key] = value*factor
        }
    }
    fun addExchangeRate(key : String,value : Double ){
        exchangeRates[key] = value
    }
    fun setExchangeRate(key : String,value : Double){
        if(exchangeRates.containsKey(key)){
            exchangeRates[key] = value
        }
    }

    private fun startCashiers( count : Int){
        repeat(count){
            cashiers.add(Cashier(this))
        }
        cashiers.forEach(){
            it.start()
        }
    }

    private fun awaitCashiers() {
        while (!transactionQueue.isEmpty()) {}
    }
    fun awaitCloseCashiers(){
        awaitCashiers()
        cashiers.forEach { it ->
            it.stopCashier()
        }
        scheduledExecutor.shutdown()
    }
    fun forceCloseCashiers(){
        cashiers.forEach { it ->
            it.stopCashier()
        }
        scheduledExecutor.shutdown()
    }

}

class Cashier(private val bank : Bank) : Thread() {
    init {
        bank.notifyObservers("Cashier started on ${currentThread()}")
    }
    private var cashierState : CashierState  = CashierState.RUNNING
    override fun run() {
        while (cashierState ==CashierState.RUNNING ) {
            val transaction = bank.transactionQueue.poll(1000,TimeUnit.MICROSECONDS) ?: continue
            try {
                when(transaction){
                    is Transaction.Deposit ->  deposit(transaction.clientId,transaction.currencyKey,transaction.amount)
                    is Transaction.Withdraw -> withdraw(transaction.clientId,transaction.currencyKey,transaction.amount)
                    is Transaction.ExchangeCurrency -> exchangeCurrency(transaction.clientId,transaction.toCurrencyKey,transaction.fromCurrencyKey,transaction.amount)
                    is Transaction.TransferFunds -> transferFunds(transaction.fromClientId,transaction.toClientId,transaction.currencyKey,transaction.amount)
                }
            }catch(e: Exception){
                when(e){
                    is ClientNotFoundException -> bank.notifyObservers("ClientId : ${e.clientId} cant be found ")
                    is InvalidTransactionException -> bank.notifyObservers("Invalid transaction data")
                    is InsufficientTransactionException -> bank.notifyObservers("Not enough currency on the client ${e.clientId} account щ")
                    else -> bank.notifyObservers(e.message)
                }
            }
        }
    }
    private fun deposit( clientId : Int, currencyKey : String, amount : Double) {
        if ( amount<=0 || !bank.exchangeRates.contains(key = currencyKey) ) throw InvalidTransactionException()
        val client = bank.clients[clientId] ?: throw ClientNotFoundException(clientId)
        synchronized(client){
            val balance = client.clientCurrency[currencyKey]?: throw InvalidTransactionException()
            client.clientCurrency[currencyKey] = balance + amount
        }
        bank.notifyObservers("Successful deposit $amount $currencyKey, to clientId : $clientId ,  ")
    }
    private fun withdraw( clientId : Int, currencyKey : String, amount : Double) {
        if ( amount<=0 || !bank.exchangeRates.contains(key = currencyKey) ) throw InvalidTransactionException()
        val client = bank.clients[clientId] ?: throw ClientNotFoundException(clientId)
        synchronized(client){
            val balance = client.clientCurrency[currencyKey]?: throw InvalidTransactionException()
            if (balance<amount ) throw  InsufficientTransactionException(clientId)
            client.clientCurrency[currencyKey] = balance-amount
        }
        bank.notifyObservers("Successful withdraw $amount $currencyKey, from clientId : $clientId ,  ")
    }
    private fun exchangeCurrency( clientId : Int, fromCurrencyKey : String, toCurrencyKey : String, amount : Double) {
        val client = bank.clients[clientId] ?: throw ClientNotFoundException(clientId)
        if ( amount<=0 ) throw InvalidTransactionException()
        synchronized(client){
            val fromBalance = client.clientCurrency[fromCurrencyKey] ?: throw InvalidTransactionException()
            val toBalance = client.clientCurrency[toCurrencyKey] ?: throw InvalidTransactionException()
            val fromCurrency = bank.exchangeRates[fromCurrencyKey]?:throw InvalidTransactionException()
            val toCurrency = bank.exchangeRates[toCurrencyKey] ?: throw InvalidTransactionException()
            val exchangeFactor = fromCurrency/toCurrency
            if(fromBalance < amount * exchangeFactor) throw InsufficientTransactionException(clientId)
            client.clientCurrency[fromCurrencyKey]=fromCurrency - amount*exchangeFactor
            client.clientCurrency[toCurrencyKey]= toCurrency + amount*exchangeFactor

            bank.notifyObservers("Exchange  $amount $fromCurrencyKey,to ${amount*exchangeFactor} $fromCurrencyKey , to clientId:$clientId")
        }
    }
    private fun transferFunds( fromClientId : Int, toClientId : Int, currencyKey : String, amount : Double) {
        if ( amount<=0 || !bank.exchangeRates.contains(key = currencyKey) ) throw InvalidTransactionException()
        val sender = bank.clients[fromClientId] ?: throw ClientNotFoundException(fromClientId)
        val receiver = bank.clients[toClientId] ?: throw ClientNotFoundException(toClientId)

        synchronized(sender){
            val senderBalance =sender.clientCurrency[currencyKey]?: throw InvalidTransactionException()
            if (senderBalance <= amount) throw InsufficientTransactionException(fromClientId)
            sender.clientCurrency[currencyKey] = senderBalance - amount
        }
        bank.notifyObservers("Transfer $amount $currencyKey,to clientId:$toClientId,from clientId:$fromClientId- withdraw completed")

        synchronized(receiver){
            val receiverBalance = receiver.clientCurrency[currencyKey]?: throw InvalidTransactionException()
            receiver.clientCurrency[currencyKey] = receiverBalance - amount
        }
        bank.notifyObservers("Successful transfer $amount $currencyKey, to clientId : $toClientId ,from clientId : $fromClientId")
    }
    fun stopCashier(){
        cashierState=CashierState.STOPPED
    }
}




fun main() {
    val fileLogger = FileLogger("logs","MyLogs")
    val logger = Logger("MyLog")
    val bank = Bank()
    bank.addObserver(logger)
    bank.addObserver(logger,fileLogger)
    val client1 = ClientFactory(bank).createClient(1)
    val client2 = ClientFactory(bank).createClient(2)
    bank.addClient(client1)
    repeat(50){
        val transaction = Transaction.Deposit(1,"USD",1.0)
        bank.addTransaction(transaction)
    }
    repeat(50){
        val transaction = Transaction.Withdraw(1,"USD",1.0)
        bank.addTransaction(transaction)
    }
    bank.awaitCloseCashiers()
    println(client1.clientCurrency["USD"])
    println(client1.clientCurrency["USD"])
    println(client1.clientCurrency["USD"])

}