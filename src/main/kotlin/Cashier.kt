import java.util.concurrent.TimeUnit

const val CASHIER_TIMEOUT  : Long = 100
enum class CashierState{
    RUNNING, BLOCKED, STOPPED
}

class Cashier(private val bank : Bank) : Thread() {

    private var cashierState : CashierState  = CashierState.RUNNING
    override fun run() {
        bank.notifyObservers("Cashier on ${Thread.currentThread()} started")
        while (cashierState == CashierState.RUNNING ) {
            //while (cashierState == CashierState.BLOCKED){}
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
        bank.notifyObservers("Successful withdraw $amount $currencyKey, from clientId : $clientId")
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

        try {
            synchronized(receiver){
                val receiverBalance = receiver.clientCurrency[currencyKey]?: throw InvalidTransactionException()
                receiver.clientCurrency[currencyKey] = receiverBalance + amount
            }
        } catch (e:InvalidTransactionException){
            synchronized(sender){
                bank.notifyObservers("Failed to transfer $amount $currencyKey, to clientId : $toClientId ,from clientId : $fromClientId")
                val senderBalance =sender.clientCurrency[currencyKey]?: throw InvalidTransactionException()
                sender.clientCurrency[currencyKey] = senderBalance - amount
                bank.notifyObservers("Return $amount $currencyKey, to clientId : $fromClientId successfully")
                throw e
            }
        }

        bank.notifyObservers("Successful transfer $amount $currencyKey, to clientId : $toClientId ,from clientId : $fromClientId")

        /*
        synchronized(sender){
            synchronized(receiver){
                val senderBalance =sender.clientCurrency[currencyKey]?: throw InvalidTransactionException()
                val receiverBalance = receiver.clientCurrency[currencyKey]?: throw InvalidTransactionException()
                if (senderBalance <= amount) throw InsufficientTransactionException(fromClientId)
                sender.clientCurrency[currencyKey] = senderBalance - amount
                receiver.clientCurrency[currencyKey] = receiverBalance + amount
            }
        }
        */
    }
    fun getCashierState() : CashierState{
        return cashierState
    }
    fun stopCashier(){
        cashierState=CashierState.STOPPED
    }
}
