file2xliff4j
============

INSTALLATION

For an introduction to the compiling, installing and using
file2xliff4j, see the document entitled "file2xliff4j: An Overview" in
the file doc/file2xliff4j_intro.html.


DEPENDENCIES (updated 6/11/2007)

file2xliff currently requires the following jar files in the class
path--at both compile and execution time:

commons-io-1.3.1.jar
jodconverter-2.2.0.jar
juh-2.2.0.jar 
jurt-2.2.0.jar 
ridl-2.2.0.jar 
slf4j-api-1.4.0.jar 
slf4j-jdk14-1.4.0.jar 
unoil-2.2.0.jar
nekohtml-0.9.5.jar (must appear before xercesImpl-2.7.1.jar in the classpath)
xercesImpl-2.7.1.jar
jpedalSTD.jar (for rudimentary--pre-alpha--PDF support)

Also, the conversion (import and export) of Word files, Excel,
PowerPoint, RTF requires an OpenOffice.org 2.x (soffice) process
listening on localhost:8100. Likewise, the export of some PDF files
(to Word, and ODT) requires OOo.

Start soffice with a command similar to:

$PATH_TO_SOFFICE/soffice -headless -norestore -accept="socket,port=8100;urp"


BUILDS

For those who prefer not to use an IDE, a build.xml file is now
included as part of the release. It has the following targets:

  setup    Creates build output directories

  jar      Creates file2xliff4j.jar (after it runs the
           setup and compile targets). (This is the
           default target.)  Output is in the build/jar
           directory.

  javadoc  Generates JavaDoc documentation in the build/doc
           directory.

  compile  Creates class files from Java source. Class files
           are placed in the build/classes directory

  clean    Deletes the build directory and all its
           subdirectories

  release  Generates the file2xliff4j.jar and javadoc
           files and places them in a date-stamped
           gzipped tar file in the build/release
           directory. 


