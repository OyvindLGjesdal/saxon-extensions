package org.expath.file;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

import org.expath.file.error.FileException;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.om.ZeroOrMore;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * XPath extension function that lists all files and directories in a given directory. 
 * 
 * @author Maarten Kroon
 * @see <a href="http://expath.org/spec/file">EXPath File Module</a>
 */
public class List extends FileFunctionDefinition {

  private static final StructuredQName qName = new StructuredQName("", EXT_NAMESPACEURI, "list");
    
  @Override
  public StructuredQName getFunctionQName() {
    return qName;
  }

  @Override
  public int getMinimumNumberOfArguments() {
    return 1;
  }

  @Override
  public int getMaximumNumberOfArguments() {
    return 3;
  }

  @Override
  public SequenceType[] getArgumentTypes() {    
    return new SequenceType[] { SequenceType.SINGLE_STRING, SequenceType.SINGLE_BOOLEAN, SequenceType.SINGLE_STRING };
  }

  @Override
  public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {    
    return SequenceType.makeSequenceType(BuiltInAtomicType.STRING, StaticProperty.ALLOWS_ZERO_OR_MORE);
  }
  
  @Override
  public boolean hasSideEffects() {    
    return false;
  }

  @Override
  public ExtensionFunctionCall makeCallExpression() {    
    return new ListCall();
  }
  
  private static class Finder extends SimpleFileVisitor<Path> {

    private final ArrayList<StringValue> stringValues = new ArrayList<>();

    private final PathMatcher matcher;
    private final boolean recursive;
    private final Path root;

    public Finder(Path root, boolean recursive, String pattern) {
      this.root = root;
      this.recursive = recursive;
      if (pattern == null) {
        this.matcher = null;
      } else {
        this.matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
      }
    }

    private void match(Path file) {
      Path name = file.getFileName();
      if (name != null && (matcher == null || matcher.matches(name))) {
        if (file.isAbsolute()) {
          file = root.relativize(file);
        }
        stringValues.add(StringValue.makeStringValue(file.toString()));
      }
    }
        
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      match(file);      
      return FileVisitResult.CONTINUE;
    }
    
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      if (dir.equals(root)) {
        return FileVisitResult.CONTINUE;
      }      
      match(dir);
      if (recursive) {
        return FileVisitResult.CONTINUE;
      } 
      return FileVisitResult.SKIP_SUBTREE;            
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) { 
      return FileVisitResult.CONTINUE;
    }
    
   // public ArrayList<Path> getPaths() {
   //   return this.paths;
   // }

    public ArrayList<StringValue> getStringValues() {
      return this.stringValues;
    }
  }
  
  private static class ListCall extends FileExtensionFunctionCall {
        
    @Override
    public ZeroOrMore<StringValue> call(XPathContext context, Sequence[] arguments) throws XPathException {      

      try {
        Path file = Paths.get(arguments[0].head().getStringValue());
        if (!Files.isDirectory(file)) {
          throw new FileException(String.format("Path \"%s\" does not point to an existing directory",
                  file.toAbsolutePath()), FileException.ERROR_PATH_NOT_DIRECTORY);
        }
        //Path rootPath = path.toAbsolutePath();
        boolean recursive = false;
        if (arguments.length > 1) {
          recursive = ((BooleanValue) arguments[1].head()).getBooleanValue();
        }
        String pattern = null;
        if (arguments.length > 2) {
          pattern = ( arguments[2].head()).getStringValue();
        }
        Finder finder = new Finder(file, recursive, pattern);
        Files.walkFileTree(file, finder);
        ArrayList<StringValue> result = finder.getStringValues();
        return new ZeroOrMore<>(finder.getStringValues().toArray(new StringValue[result.size()]));
      } catch (FileException fe) {
        throw fe;
      } catch (Exception e) {
        throw new  FileException("Other file error", e, FileException.ERROR_IO);
      }
    } 
  }
}