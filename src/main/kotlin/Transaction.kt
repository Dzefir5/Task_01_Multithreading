sealed class Transaction{
    data class Deposit(val clientId : Int,val currencyKey : String, val amount : Double):Transaction()
    data class Withdraw(val clientId : Int,val currencyKey : String, val amount : Double):Transaction()
    data class ExchangeCurrency(val clientId : Int,val fromCurrencyKey : String,val toCurrencyKey : String, val amount : Double):Transaction()
    data class TransferFunds(val fromClientId : Int,val toClientId : Int,val currencyKey : String, val amount : Double):Transaction()
}