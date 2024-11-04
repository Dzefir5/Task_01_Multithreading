# Многопоточное приложение на Kotlin симулирующее работу банка с кассами

---

## Основные элементы
- ``` class Bank(private val start : Boolean = true)``` -класс Bank, создаётся с параметром start(Boolean) отвечающим за то сразу ли начнётся обработка транзакций
- ``` class Client(...) ``` - класс Client, содержащий в себе информацию ID клиента и информацию о валютах на его счету
- ``` class Cashier(private val bank : Bank) : Thread() ``` - класс Cashier занимающийся обработкой поступающих транзакций

- - - 

```kotlin
sealed class Transaction{
    data class Deposit(val clientId : Int,val currencyKey : String, val amount : Double):Transaction()
    data class Withdraw(val clientId : Int,val currencyKey : String, val amount : Double):Transaction()
    data class ExchangeCurrency(val clientId : Int,val fromCurrencyKey : String,val toCurrencyKey : String, val amount : Double):Transaction()
    data class TransferFunds(val fromClientId : Int,val toClientId : Int,val currencyKey : String, val amount : Double):Transaction()
}
```
- Класс Transaction через чьиx наследников реализуется работа различных видов транзакций

- - -

## Вспомогательные элементы
```kotlin
interface Observer{
    fun update(message : String?)
}
```
- Интерфейс Observer на основе которого реализуются классы для логгирования такие как :
    - ```DefaultLogger(private val loggerTag : String = "DefaultLog") ``` - стандартный логгер выводящий сообщения в терминал
    - ```FileLogger(...) ``` - логгер создающий для каждого запуска отдельный файл куда будут записываться логи

```kotlin
open class BankException():Exception()

class ClientNotFoundException(val clientId : Int) : BankException()
class InvalidTransactionException() : BankException()
class InsufficientTransactionException(val clientId : Int) : BankException()
```
- класс BankException() для имплементации исключений при работае банка
- - - 
# Bank
## Описание методов
- Управление кассами
  - ```addCashiers( count : Int)``` - Добавляет некоторое количество касс
  - ```awaitCashiers(timeout: Long = 0L)``` - Блокирует текущий поток ожидая окончания выполнения всех текущих транзакций,или пока не истечёт время timeout( если timeout = 0 , то будет ждать опустошения очереди транзакций)
  - ```awaitCloseCashiers(timeout: Long = 0)``` - Также блокирует поток как и ```awaitCashiers(...) ``` ,после чего закрывает все кассы
  - ```forceCloseCashiers()``` - Блокирует текущий поток и закрывает все кассы , даже если все очередь транзакций не выполнена
- Управление наблюдателями
  - ```addObserver(observer: Observer)```: Добавляет одного наблюдателя.
  - ```addObserver(observerCollection: Collection<Observer>```: Добавляет коллекцию наблюдателей.
  - ```addObserver(vararg observerList: Observer)```: Добавляет нескольких наблюдателей.
  - ```notifyObservers(message: String?)```: Оповещает всех наблюдателей
- Управление клиентами
  - ```addClient(client: Client)```: Добавляет одного клиента и уведомляет наблюдателей.
  - ```addClient(clientCollection: Collection<Client>)```: Добавляет нескольких клиентов из коллекции.
  - ```addClient(vararg clientsList: Client)```: Добавляет нескольких клиентов.
- Управление транзакциями
  - ```addTransaction(transaction: Transaction)```: Добавляет одну транзакцию в очередь.
  - ```addTransaction(transactionCollection: Collection<Transaction>)```: Добавляет несколько транзакций из коллекции.
  - ```addTransaction(vararg transactionList: Transaction)```: Добавляет несколько транзакций.
- Управление курсами валют
  - ```addExchangeRate(key: String, value: Double)```: Добавляет курс валюты.
  - ```setExchangeRate(key: String, value: Double)```: Обновляет существующий курс валюты.
- Управление очередью
  - ```clearQueue()```: Очищает очередь транзакций.

- - -
## Пример использования
```kotlin
fun main() {
    //Создаём логгер для записи в файлы
    //"logs" - относительная директория для папки с логами
    //"MyLogs" - ключевая часть имени файлов 
    //"txt" - расиширения для файлов
    val fileLogger = FileLogger("logs","MyLogs","txt")

    //Создаём обычный логгер
    // "MyLog" - тэг для вывода логов
    val logger = DefaultLogger("MyLog")

    //Создаём экземпляр класса банка,запускать будем вручную
    val bank = Bank(false)
    //Добавляем наблюдатели
    bank.addObserver(fileLogger,logger)
    //Создаём двух клиентов используя ClientFactory().createClient
    val client1 = ClientFactory(bank).createClient(1)
    val client2 = ClientFactory(bank).createClient(2)
    //Добавляем клиентов
    bank.addClient(client1,client2)\
    //Делаем депозиты клиентам
    bank.addTransaction(Transaction.Deposit(1,"USD",1000.0))
    bank.addTransaction(Transaction.Deposit(2,"USD",1000.0))
    //Запускаем банк
    bank.start()

    //Выполняем 5000 транзакций по записи/списыванию одной и той же суммы
    repeat(5000){
        val transaction1 = Transaction.Deposit(1,"USD",1.0)
        val transaction2 = Transaction.Withdraw(1,"USD",1.0)
        bank.addTransaction(transaction1)
        bank.addTransaction(transaction2)
    }
    //выводи не ожидая выполнения : вывод может быть как 1000 так и 1001
    println(client1.clientCurrency["USD"])
    //ждём выполнения
    bank.awaitCashiers()
    //вывод будет гарантированно 1000
    println(client1.clientCurrency["USD"])
    //выполняем перевод средств 1->2 и обратно
    repeat(2500){
        bank.addTransaction(Transaction.TransferFunds(1,2,"USD",10.0))
        bank.addTransaction(Transaction.TransferFunds(2,1,"USD",10.0))
    }
    //принудительно завершим работу
    bank.awaitCloseCashiers()
    println(client1.clientCurrency["USD"])  // вывод 990 или 1000
    println(client2.clientCurrency["USD"])  // вывод 1010 или 1000

}

```
