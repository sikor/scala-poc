package streams.benchmarks

/**
  * Created by Paweł Sikora.
  */
class HashCodeHolder(val hc: Int) {


  def canEqual(other: Any): Boolean = other.isInstanceOf[HashCodeHolder]

  override def equals(other: Any): Boolean = canEqual(other) && (this eq other.asInstanceOf[HashCodeHolder])

  override def hashCode(): Int = {
    hc
  }
}
