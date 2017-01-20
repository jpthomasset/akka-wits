import com.frenchcoder.wits.{ ServiceMessage, ServiceTag }

case class SampleServiceTag(version: Int = 1) extends ServiceTag
case class ToUpperRequest(text: String) extends ServiceMessage

case class ToUpperResponse(text: String)

