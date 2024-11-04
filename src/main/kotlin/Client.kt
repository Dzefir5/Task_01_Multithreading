import java.util.concurrent.ConcurrentHashMap

class ClientFactory(private val bank:Bank){
    fun createClient(id: Int) : Client{
        val client = Client(id , ConcurrentHashMap<String, Double>())
        bank.exchangeRates.forEach{ (currency, _) ->
            client.clientCurrency[currency]= 0.0
        }
        return client
    }
}
class Client(
    val id: Int,
    var clientCurrency : ConcurrentHashMap<String, Double>
)