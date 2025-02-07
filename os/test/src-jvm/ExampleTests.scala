package test.os

import java.nio.file.attribute.{GroupPrincipal, FileTime}

import utest._
object ExampleTests extends TestSuite {

  val tests = Tests {
    test("splash") - TestUtil.prep { wd =>
      if (Unix()) {
        // Make sure working directory exists and is empty
        val wd = os.pwd / "out/splash"
        os.remove.all(wd)
        os.makeDir.all(wd)

        os.write(wd / "file.txt", "hello")
        os.read(wd / "file.txt") ==> "hello"

        os.copy(wd / "file.txt", wd / "copied.txt")
        os.list(wd) ==> Seq(wd / "copied.txt", wd / "file.txt")

        val invoked = os.proc("cat", wd / "file.txt", wd / "copied.txt").call(cwd = wd)
        invoked.out.trim() ==> "hellohello"
      }
    }

    test("concatTxt") - TestUtil.prep { wd =>
      // Find and concatenate all .txt files directly in the working directory
      os.write(
        wd / "all.txt",
        os.list(wd).filter(_.ext == "txt").map(os.read)
      )

      os.read(wd / "all.txt") ==>
        """I am cowI am cow
          |Hear me moo
          |I weigh twice as much as you
          |And I look good on the barbecue""".stripMargin
    }

    test("subprocessConcat") - TestUtil.prep { wd =>
      val catCmd = if (scala.util.Properties.isWin) "type" else "cat"
      // Find and concatenate all .txt files directly in the working directory
      TestUtil.proc(catCmd, os.list(wd).filter(_.ext == "txt"))
        .call(stdout = wd / "all.txt")

      os.read(wd / "all.txt") ==>
        """I am cowI am cow
          |Hear me moo
          |I weigh twice as much as you
          |And I look good on the barbecue""".stripMargin
    }

    test("curlToTempFile") - TestUtil.prep { wd =>
      if (Unix()) {
        // Curl to temporary file
        val temp = os.temp()
        os.proc("curl", "-L", ExampleResourcess.RemoteReadme.url)
          .call(stdout = temp)

        os.size(temp) ==> ExampleResourcess.RemoteReadme.size

        // Curl to temporary file
        val temp2 = os.temp()
        val proc = os.proc("curl", "-L", ExampleResourcess.RemoteReadme.url).spawn()

        os.write.over(temp2, proc.stdout)
        os.size(temp2) ==> ExampleResourcess.RemoteReadme.size

        assert(os.size(temp) == os.size(temp2))
      }
    }

    test("lineCount") - TestUtil.prep { wd =>
      // Line-count of all .txt files recursively in wd
      val lineCount = os.walk(wd)
        .filter(_.ext == "txt")
        .map(os.read.lines)
        .map(_.size)
        .sum

      lineCount ==> 9
    }

    test("largestThree") - TestUtil.prep { wd =>
      // Find the largest three files in the given folder tree
      val largestThree = os.walk(wd)
        .filter(os.isFile(_, followLinks = false))
        .map(x => os.size(x) -> x).sortBy(-_._1)
        .take(3)

      // on unix it is 81 bytes, win adds 3 bytes (3 \r characters)
      val multilineSizes = Set[Long](81, 84)
      assert(multilineSizes contains os.stat(wd / "Multi Line.txt").size)

      // ignore multiline (second file) because its size varies
      largestThree.filterNot(_._2.last == "Multi Line.txt") ==> Seq(
        (711, wd / "misc/binary.png"),
        (22, wd / "folder1/one.txt")
      )
    }

    test("moveOut") - TestUtil.prep { wd =>
      // Move all files inside the "misc" folder out of it
      import os.{GlobSyntax, /}
      os.list(wd / "misc").map(os.move.matching { case p / "misc" / x => p / x })
    }

    test("frequency") - TestUtil.prep { wd =>
      // Calculate the word frequency of all the text files in the folder tree
      def txt = os.walk(wd).filter(_.ext == "txt").map(os.read)
      def freq(s: Seq[String]) = s.groupBy(x => x).mapValues(_.length).toSeq
      val map = freq(txt.flatMap(_.split("[^a-zA-Z0-9_]"))).sortBy(-_._2)
      map
    }
    test("comparison") {

      os.remove.all(os.pwd / "out/scratch/folder/thing/file")
      os.write(
        os.pwd / "out/scratch/folder/thing/file",
        "Hello!",
        createFolders = true
      )

      def removeAll(path: String) = {
        def getRecursively(f: java.io.File): Seq[java.io.File] = {
          f.listFiles.filter(_.isDirectory).flatMap(getRecursively) ++ f.listFiles
        }
        getRecursively(new java.io.File(path)).foreach { f =>
          println(f)
          if (!f.delete())
            throw new RuntimeException("Failed to delete " + f.getAbsolutePath)
        }
        new java.io.File(path).delete
      }
      removeAll("out/scratch/folder/thing")

      assert(os.list(os.pwd / "out/scratch/folder").toSeq == Nil)

      os.write(
        os.pwd / "out/scratch/folder/thing/file",
        "Hello!",
        createFolders = true
      )

      os.remove.all(os.pwd / "out/scratch/folder/thing")
      assert(os.list(os.pwd / "out/scratch/folder").toSeq == Nil)
    }

    test("constructingPaths") {

      // Get the process' Current Working Directory. As a convention
      // the directory that "this" code cares about (which may differ
      // from the pwd) is called `wd`
      val wd = os.pwd

      // A path nested inside `wd`
      wd / "folder/file"

      // A path starting from the root
      os.root / "folder/file"

      // A path with spaces or other special characters
      wd / "My Folder/My File.txt"

      // Up one level from the wd
      wd / os.up

      // Up two levels from the wd
      wd / os.up / os.up
    }
    test("newPath") {

      val target = os.pwd / "out/scratch"
      val target2: os.Path = "/out/scratch" // literal syntax
    }
    test("relPaths") {

      // The path "folder/file"
      val rel1 = os.rel / "folder/file"
      val rel2 = os.rel / "folder/file"
      val rel3: os.RelPath = "folder/file" // literal syntax

      // The relative difference between two paths
      val target = os.pwd / "out/scratch/file"
      assert((target relativeTo os.pwd) == os.rel / "out/scratch/file")

      // `up`s get resolved automatically
      val minus = os.pwd relativeTo target
      val ups = os.up / os.up / os.up
      assert(minus == ups)
      (
        rel1: os.RelPath,
        rel2: os.RelPath
      )
    }
    test("subPaths") {

      // The path "folder/file"
      val sub1 = os.sub / "folder/file"
      val sub2 = os.sub / "folder/file"
      val sub3 = "folder/file" // literal syntax

      // The relative difference between two paths
      val target = os.pwd / "out/scratch/file"
      assert((target subRelativeTo os.pwd) == os.sub / "out/scratch/file")

      // Converting os.RelPath to os.SubPath
      val rel3 = os.rel / "folder/file"
      val sub4 = rel3.asSubPath

      // `up`s are not allowed in sub paths
      intercept[Exception](os.pwd subRelativeTo target)
    }
    test("relSubPathEquality") {
      assert(
        (os.sub / "hello/world") == (os.rel / "hello/world"),
        os.sub == os.rel
      )
    }
    test("relPathCombine") {
      val target = os.pwd / "out/scratch/file"
      val rel = target relativeTo os.pwd
      val newBase = os.root / "code/server"
      assert(newBase / rel == os.root / "code/server/out/scratch/file")
    }
    test("subPathCombine") {
      val target = os.pwd / "out/scratch/file"
      val sub = target subRelativeTo os.pwd
      val newBase = os.root / "code/server"
      assert(
        newBase / sub == os.root / "code/server/out/scratch/file",
        sub / sub == os.sub / "out/scratch/file/out/scratch/file"
      )
    }
    test("pathUp") {
      val target = os.root / "out/scratch/file"
      assert(target / os.up == os.root / "out/scratch")
    }
    test("relPathUp") {
      val target = os.rel / "out/scratch/file"
      assert(target / os.up == os.rel / "out/scratch")
    }
    test("relPathUp") {
      val target = os.sub / "out/scratch/file"
      assert(target / os.up == os.sub / "out/scratch")
    }
    test("canonical") {
      if (Unix()) {

        assert((os.root / "folder/file" / os.up).toString == "/folder")
        // not "/folder/file/.."

        assert((os.rel / "folder/file" / os.up).toString == "folder")
        // not "folder/file/.."
      }
    }
    test("findWc") {

      val wd = os.Path(sys.env("OS_TEST_RESOURCE_FOLDER")) / "test"

      // find . -name '*.txt' | xargs wc -l
      val lines = os.walk(wd)
        .filter(_.ext == "txt")
        .map(os.read.lines)
        .map(_.length)
        .sum

      assert(lines == 9)
    }

    test("rename") {
//      val d1/"omg"/x1 = wd
//      val d2/"omg"/x2 = wd
//      ls! wd |? (_.ext == "scala") | (x => mv! x ! x.pref)
    }
    test("allSubpathsResolveCorrectly") {

      for (abs <- os.walk(os.pwd)) {
        val rel = abs.relativeTo(os.pwd)
        assert(rel.ups == 0)
        assert(os.pwd / rel == abs)
      }
    }
  }
}
