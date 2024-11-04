open class BankException():Exception()

class ClientNotFoundException(val clientId : Int) : BankException()
class InvalidTransactionException() : BankException()
class InsufficientTransactionException(val clientId : Int) : BankException()
