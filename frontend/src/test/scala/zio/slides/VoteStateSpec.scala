//package zio.slides
//
//import zio.random.Random
//import zio.slides.VoteState.{CastVoteId, Topic, UserId, Vote}
//import zio.test.Assertion.{anything, equalTo, isLessThan, isLessThanEqualTo}
//import zio.test._
//import zio.test.magnolia.DeriveGen
//
//object VoteStateSpec extends DefaultRunnableSpec {
//
//  val voteGen: Gen[Random with Sized, Vote]            = Gen.elements(List.tabulate(3)(n => Vote(s"vote-${n + 1}")): _*)
//  val topicGen: Gen[Random with Sized, Topic]          = Gen.elements(List.tabulate(3)(n => Topic(s"topic-${n + 1}")): _*)
//  val userIdGen: Gen[Random with Sized, UserId]        = Gen.elements(List.tabulate(20)(n => UserId(s"user-id-${n + 1}")): _*)
//  val castVotesGen: Gen[Random with Sized, CastVoteId] = Gen.zipN(userIdGen, topicGen, voteGen)(CastVoteId(_, _, _))
//
//  override def spec = suite("VoteState")(
//    testM("vote totals per topic can never exceed the number of users") {
//      check(Gen.listOf(castVotesGen)) { votes =>
//        val voteState = votes.foldLeft(VoteState.empty)(_.processUpdate(_))
//        val users     = votes.map(_.id).toSet
//        val topics    = votes.map(_.topic).toSet
//
//        BoolAlgebra
//          .all(
//            topics.map { topic =>
//              val totalVotes = voteState.voteTotals(topic).values.sum
//              assert(totalVotes)(isLessThanEqualTo(users.size))
//            }.toList
//          )
//          .getOrElse(assert(1)(anything))
//      }
//    },
//    test("processes votes") {
//      val userA = UserId("A")
//      val userB = UserId("B")
//      val userC = UserId("C")
//
//      val topicRed  = Topic("Red")
//      val topicBlue = Topic("Blue")
//
//      val votes = List(
//        CastVoteId(userC, topicRed, Vote("2")),
//        CastVoteId(userA, topicRed, Vote("3")),
//        CastVoteId(userA, topicBlue, Vote("1")),
//        CastVoteId(userA, topicBlue, Vote("2")),
//        CastVoteId(userB, topicBlue, Vote("1")),
//        CastVoteId(userA, topicRed, Vote("1")),
//        CastVoteId(userC, topicBlue, Vote("3")),
//        CastVoteId(userB, topicRed, Vote("2"))
//      )
//
//      val voteState = votes.foldLeft(VoteState.empty)(_.processUpdate(_))
//
//      assert(voteState.voteTotals(topicRed))(equalTo(Map(Vote("1") -> 1, Vote("2") -> 2))) &&
//      assert(voteState.voteTotals(topicBlue))(equalTo(Map(Vote("1") -> 1, Vote("2") -> 1, Vote("3") -> 1)))
//    }
//  )
//
//}
