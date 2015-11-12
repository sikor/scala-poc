package streams.benchmarks

/**
  * Created by Paweł Sikora.
  */
class SynchState {

  var value = 0l

  def updateState(msg: Long): Long = {
    this.synchronized {
      value += msg
    }
    msg
  }

}
