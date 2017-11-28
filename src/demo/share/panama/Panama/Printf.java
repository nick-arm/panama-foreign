/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nicl.NativeLibrary;
import java.nicl.NativeScope;
import java.nicl.Scope;
import java.nicl.types.Pointer;
import java.nicl.types.Transformer;

public class Printf {
    /**
     * Simple printf example using normal method invocation
     */
    public void testPrintf() {
        stdio i = NativeLibrary.bindRaw(stdio.class);

        // Create a scope to allocate things in
        Scope scope = new NativeScope();

        // Convert the Java string to a native one
        // Basically uses Unsafe to allocate memory and copy the bytes
        Pointer<Byte> fmt = Transformer.toCString("Hello, World!\n", scope);

        // Call printf
        i.printf(fmt);

        // Make sure output is not stuck in buffer
        i.fflush(null);
    }


    /**
     * Simple printf example using method handle
     */
    public void testPrintfUsingMethodHandle() throws Throwable {
        stdio i = NativeLibrary.bindRaw(stdio.class);

        // Create a MH for the printf function
        MethodHandle printf = MethodHandles.publicLookup().findVirtual(stdio.class, "printf", MethodType.methodType(int.class, Pointer.class, Object[].class));

        // Create a scope to allocate things in
        Scope scope = new NativeScope();

        // Convert the Java string to a native one
        Pointer<Byte> fmt = Transformer.toCString("Hello, %d!\n", scope);

        // Call printf
        printf.invoke(i, fmt, 4711);

        // Make sure output is not stuck in buffer
        i.fflush(null);
    }


    /**
     * printf with an integer arg
     */
    public void testPrintfWithIntegerArg() throws Throwable {
        stdio i = NativeLibrary.bindRaw(stdio.class);

        // Lookup a MH for the printf function
        MethodHandle printf = Util.lookup(Util.Function.PRINTF);

        // Create a scope to allocate things in
        Scope scope = new NativeScope();

        Pointer<Byte> fmt = Transformer.toCString("Hello, %d!\n", scope);
        printf.invoke(i, fmt, 4711);

        // Make sure output is not stuck in buffer
        i.fflush(null);
    }

    /**
     * printf with a string argument
     */
    public void testPrintfWithStringArg() throws Throwable {
        stdio i = NativeLibrary.bindRaw(stdio.class);

        Scope scope = new NativeScope();

        // Lookup a MH for the printf function
        MethodHandle printf = Util.lookup(Util.Function.PRINTF);

        Pointer<Byte> fmt = Transformer.toCString("Hello, %s!\n", scope);
        Pointer<Byte> arg = Transformer.toCString("World", scope);

        printf.invoke(i, fmt, arg);

        // Make sure output is not stuck in buffer
        i.fflush(null);
    }

    public static void main(String[] args) throws Throwable {
        Printf h = new Printf();

        h.testPrintf();
        h.testPrintfUsingMethodHandle();
        h.testPrintfWithIntegerArg();
        h.testPrintfWithStringArg();
    }
}
