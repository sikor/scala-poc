package benchmarks

/**
  * Created by Paweł Sikora.
  */
class SimpleState {

  var value = 0l

  def updateState(msg: Long): Long = {
    value += msg
    msg
  }
}
