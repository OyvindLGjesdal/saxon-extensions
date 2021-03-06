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

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.expath.file.error.FileException;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

/**
 * XPath extension function that copies a file or a directory given a source and a target path/URI.
 * 
 * @author Maarten Kroon
 * @see <a href="http://expath.org/spec/file">EXPath File Module</a>
 */
public class Copy extends FileFunctionDefinition {

  private static final StructuredQName qName = new StructuredQName("", EXT_NAMESPACEURI, "copy");

  @Override
  public StructuredQName getFunctionQName() {
    return qName;
  }

  @Override
  public int getMinimumNumberOfArguments() {
    return 2;
  }

  @Override
  public int getMaximumNumberOfArguments() {
    return 2;
  }

  @Override
  public SequenceType[] getArgumentTypes() {    
    return new SequenceType[] { SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING };
  }

  @Override
  public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {    
    return SequenceType.OPTIONAL_BOOLEAN;
  }
  
  @Override
  public boolean hasSideEffects() {    
    return true;
  }

  @Override
  public ExtensionFunctionCall makeCallExpression() {    
    return new CopyCall();
  }
  
  private static class CopyCall extends FileExtensionFunctionCall {
        
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {      
      try {         
        File sourceFile = getFile(((StringValue) arguments[0].head()).getStringValue());
        File targetFile = getFile(((StringValue) arguments[1].head()).getStringValue());
        if (!sourceFile.exists()) {
          throw new FileException(String.format("Source path \"%s\" does not exist", 
              sourceFile.getAbsolutePath()), FileException.ERROR_PATH_NOT_EXIST);
        }
        if (sourceFile.isDirectory() && targetFile.isFile()) {
          throw new FileException(String.format("Source \"%s\" points to a directory and target \"%s\" points to an existing file", 
              sourceFile.getAbsolutePath(), targetFile.getAbsolutePath()), FileException.ERROR_PATH_EXISTS);
        }
        File sourceParent = sourceFile.getParentFile();
        if (!sourceParent.exists()) {
          throw new FileException(String.format("Parent directory of source \"%s\" does not exist", 
              sourceFile.getAbsolutePath()), FileException.ERROR_PATH_NOT_DIRECTORY);
        }
        File targetDir = new File(targetFile, sourceFile.getName());        
        if (sourceFile.isFile() && targetDir.isDirectory()) {
          throw new FileException(String.format("Source \"%s\" points to a file and target \"%s\" points to a directory, in which a subdirectory exists with the name of the source file", 
              sourceFile.getAbsolutePath(), targetFile.getAbsolutePath()), FileException.ERROR_PATH_IS_DIRECTORY);
        }        
        if (sourceFile.isFile()) {          
          if (!targetFile.exists() || targetFile.isFile()) {
            FileUtils.copyFile(sourceFile, targetFile);
          } else if (targetFile.isDirectory()) {
            FileUtils.copyFileToDirectory(sourceFile, targetFile);
          }                    
        } else if (sourceFile.isDirectory()) {          
          if (!targetFile.exists()) {            
            FileUtils.copyDirectory(sourceFile, targetFile);           
          } else if (targetFile.isDirectory()) {
            FileUtils.copyDirectoryToDirectory(sourceFile, targetFile);
          }          
        }        
        return EmptySequence.getInstance();
      } catch (FileException fe) {
        throw fe;
      } catch (Exception e) {
        throw new FileException("Other file error", e, FileException.ERROR_IO);
      }
    } 
  }
}