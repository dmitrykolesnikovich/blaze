/*
 * Copyright 2015 Fizzed, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fizzed.blaze.shell;

import com.fizzed.blaze.Action;
import com.fizzed.blaze.BlazeException;
import com.fizzed.blaze.Context;
import com.fizzed.blaze.util.ObjectHelper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;

/**
 *
 * @author joelauer
 */
public class Exec extends Action<ExecResult> implements PathSupport<Exec> {

    final private Which which;
    final private ProcessExecutor executor;
    final private List<String> arguments;
    
    public Exec(Context context) {
        super(context);
        // which will be used to locate the executable
        this.which = new Which(context);
        this.arguments = new ArrayList<>();
        this.executor = new ProcessExecutor()
            .redirectInput(System.in)
            .redirectOutput(System.out)
            .redirectErrorStream(true)
            // initialize executable to context of current project basedir
            .directory(context.baseDir())
            .exitValueNormal();
    }
    
    public Exec command(String command, Object... arguments) {
        this.which.command(command);
        this.arguments.clear();
        this.arguments.addAll(ObjectHelper.toStringList(arguments));
        return this;
    }
    
    /**
     * Adds one or more arguments by appending to existing list.
     * @param arguments
     * @return 
     * @see #args(java.lang.Object...) For replacing existing arguments
     */
    public Exec arg(Object... arguments) {
        this.arguments.addAll(ObjectHelper.toStringList(arguments));
        return this;
    }
    
    /**
     * Replaces existing arguments with one or more new arguments.
     * @param arguments
     * @return 
     * @see #arg(java.lang.Object...) For adding to existing arguments rather
     *      than replacing
     */
    public Exec args(Object... arguments) {
        this.arguments.clear();
        this.arguments.addAll(ObjectHelper.toStringList(arguments));
        return this;
    }
    
    @Override
    public List<Path> getPaths() {
        return this.which.getPaths();
    }

    public Exec env(String name, String value) {
        this.executor.environment(name, value);
        return this;
    }
    
    public Exec workingDir(Path path) {
        this.executor.directory(context.withBaseDir(path));
        return this;
    }
    
    public Exec workingDir(File path) {
        this.executor.directory(context.withBaseDir(path));
        return this;
    }
    
    public Exec workingDir(String dir) {
        this.executor.directory(context.withBaseDir(Paths.get(dir)));
        return this;
    }

    public Exec readOutput() {
        this.executor.readOutput(true);
        return this;
    }

    public Exec timeout(long timeoutInMillis) {
        this.executor.timeout(timeoutInMillis, TimeUnit.MILLISECONDS);
        return this;
    }
    
    public Exec timeout(long timeout, TimeUnit units) {
        this.executor.timeout(timeout, units);
        return this;
    }

    @Override
    public ExecResult doRun() throws BlazeException {
        File exeFile = this.which.run();
        
        if (exeFile == null) {
            throw new ExecutableNotFoundException("Executable '" + this.which.getCommand() + "' not found");
        }
        
        // build final list of command to execute (executable first then args)
        List<String> command = new ArrayList<>();
        
        command.add(exeFile.getAbsolutePath());
        
        command.addAll(arguments);
        
        this.executor.command(command);
        
        try {
            return new ExecResult(this.executor.execute());
        } catch (IOException | InterruptedException | TimeoutException | InvalidExitValueException e) {
            throw new BlazeException("Unable to cleanly execute", e);
        }
    }

}
