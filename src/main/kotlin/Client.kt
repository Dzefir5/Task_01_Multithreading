import java.util.concurrent.ConcurrentHashMap

class ClientFactory(private val bank:Bank){
    fun createClient(id: Int = (0..Int.MAX_VALUE).random()) : Client{
        var clientId = id
        while (bank.clients.containsKey(clientId)){
            clientId =(0..Int.MAX_VALUE).random()
        }
        val client = Client(clientId , ConcurrentHashMap<String, Double>())
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