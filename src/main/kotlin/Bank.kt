
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.lang.Thread.State.NEW
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

const val RUB_DEF = 130.0
const val USD_DEF = 1.0
const val EUR_DEF = 1.29
const val DEFAULT_CASHIERS_COUNT = 5

class Bank(private val start : Boolean = true) {
    private val observers = mutableListOf<Observer>()
    val clients = ConcurrentHashMap<Int,Client>()
    val exchangeRates = ConcurrentHashMap<String, Double>()
    val transactionQueue = LinkedBlockingQueue<Transaction>()
    val cashiers = ArrayList<Cashier>()
    val scheduledExecutor = ScheduledThreadPoolExecutor(1)
    init {
        addCashiers(DEFAULT_CASHIERS_COUNT)
        if(start){
            startCashiers()
            initExchangeRate()
        }
    }
    fun start(){
        if(!start){
            startCashiers()
            initExchangeRate()
        }
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
    fun clearQueue(){
        transactionQueue.clear()
    }

    private fun addCashiers( count : Int){
        repeat(count){
            cashiers.add(Cashier(this))
        }
    }
    private fun startCashiers(){
        cashiers.forEach(){
            if(it.state==NEW) it.start()
        }
    }

    fun awaitCashiers(timeout: Long = 0L) {
        if(!start) return
        runBlocking(){
            if (timeout == 0L) {
                while (!transactionQueue.isEmpty()) {}
            } else {
                withTimeout(timeout) {
                    while (!transactionQueue.isEmpty()) {}
                }
            }
        }
    }
    fun awaitCloseCashiers(timeout: Long = 0){
        if(!start) return
        runBlocking {
            awaitCashiers(timeout)
            cashiers.forEachIndexed { index, it ->
                it.stopCashier()
                it.join()
            }
            cashiers.clear()
            scheduledExecutor.shutdown()
        }
    }
    fun forceCloseCashiers() {
        if(!start) return
        runBlocking {
            cashiers.forEach { it ->
                it.stopCashier()
                it.join()
            }
            cashiers.clear()
            scheduledExecutor.shutdown()
        }
    }
}
