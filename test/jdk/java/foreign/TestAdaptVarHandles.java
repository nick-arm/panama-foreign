/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @modules jdk.incubator.foreign
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAdaptVarHandles
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAdaptVarHandles
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false -Xverify:all TestAdaptVarHandles
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true -Xverify:all TestAdaptVarHandles
 */

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.*;
import static org.testng.Assert.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.function.Function;

public class TestAdaptVarHandles {

    static MethodHandle S2I;
    static MethodHandle I2S;
    static MethodHandle S2L;
    static MethodHandle S2L_EX;
    static MethodHandle S2I_EX;
    static MethodHandle I2S_EX;
    static MethodHandle BASE_ADDR;
    static MethodHandle SUM_OFFSETS;
    static MethodHandle VOID_FILTER;

    static {
        try {
            S2I = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "stringToInt", MethodType.methodType(int.class, String.class));
            I2S = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "intToString", MethodType.methodType(String.class, int.class));
            S2L = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "stringToLong", MethodType.methodType(long.class, String.class));
            S2L_EX = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "stringToLongException", MethodType.methodType(long.class, String.class));
            BASE_ADDR = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "baseAddress", MethodType.methodType(MemoryAddress.class, MemorySegment.class));
            SUM_OFFSETS = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "sumOffsets", MethodType.methodType(long.class, long.class, long.class));
            VOID_FILTER = MethodHandles.lookup().findStatic(TestAdaptVarHandles.class, "void_filter", MethodType.methodType(void.class, String.class));

            MethodHandle s2i_ex = MethodHandles.throwException(int.class, Throwable.class);
            s2i_ex = MethodHandles.insertArguments(s2i_ex, 0, new Throwable());
            S2I_EX = MethodHandles.dropArguments(s2i_ex, 0, String.class);

            MethodHandle i2s_ex = MethodHandles.throwException(String.class, Throwable.class);
            i2s_ex = MethodHandles.insertArguments(i2s_ex, 0, new Throwable());
            I2S_EX = MethodHandles.dropArguments(i2s_ex, 0, int.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError();
        }
    }

    @Test
    public void testFilterValue() throws Throwable {
        ValueLayout layout = MemoryLayouts.JAVA_INT;
        MemorySegment segment = MemorySegment.allocateNative(layout);
        VarHandle intHandle = layout.varHandle(int.class);
        VarHandle i2SHandle = MethodHandles.filterValue(intHandle, S2I, I2S);
        i2SHandle.set(segment.baseAddress(), "1");
        String oldValue = (String)i2SHandle.getAndAdd(segment.baseAddress(), "42");
        assertEquals(oldValue, "1");
        String value = (String)i2SHandle.get(segment.baseAddress());
        assertEquals(value, "43");
        boolean swapped = (boolean)i2SHandle.compareAndSet(segment.baseAddress(), "43", "12");
        assertTrue(swapped);
        oldValue = (String)i2SHandle.compareAndExchange(segment.baseAddress(), "12", "42");
        assertEquals(oldValue, "12");
        value = (String)i2SHandle.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment.baseAddress());
        assertEquals(value, "42");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadFilterNullTarget() {
        MethodHandles.filterValue(null, S2I, I2S);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadFilterNullUnbox() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(intHandle, null, I2S);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadFilterNullBox() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(intHandle, S2I, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterCarrier() {
        VarHandle floatHandle = MemoryLayouts.JAVA_FLOAT.varHandle(float.class);
        MethodHandles.filterValue(floatHandle, S2I, I2S);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterUnboxArity() {
        VarHandle floatHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(floatHandle, S2I.bindTo(""), I2S);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterBoxArity() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(intHandle, S2I, I2S.bindTo(42));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterBoxException() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(intHandle, I2S, S2L_EX);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterUnboxException() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(intHandle, S2L_EX, I2S);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterBoxHandleException() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(intHandle, S2I, I2S_EX);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterUnboxHandleException() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterValue(intHandle, S2I_EX, I2S);
    }

    @Test
    public void testFilterCoordinates() throws Throwable {
        ValueLayout layout = MemoryLayouts.JAVA_INT;
        MemorySegment segment = MemorySegment.allocateNative(layout);
        VarHandle intHandle = MemoryHandles.withStride(layout.varHandle(int.class), 4);
        VarHandle intHandle_longIndex = MethodHandles.filterCoordinates(intHandle, 0, BASE_ADDR, S2L);
        intHandle_longIndex.set(segment, "0", 1);
        int oldValue = (int)intHandle_longIndex.getAndAdd(segment, "0", 42);
        assertEquals(oldValue, 1);
        int value = (int)intHandle_longIndex.get(segment, "0");
        assertEquals(value, 43);
        boolean swapped = (boolean)intHandle_longIndex.compareAndSet(segment, "0", 43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_longIndex.compareAndExchange(segment, "0", 12, 42);
        assertEquals(oldValue, 12);
        value = (int)intHandle_longIndex.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment, "0");
        assertEquals(value, 42);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadFilterCoordinatesNullTarget() {
        MethodHandles.filterCoordinates(null, 0, S2I);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadFilterCoordinatesNullFilters() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterCoordinates(intHandle, 0, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterCoordinatesNegativePos() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterCoordinates(intHandle, -1, SUM_OFFSETS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterCoordinatesPosTooBig() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.filterCoordinates(intHandle, 1, SUM_OFFSETS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterCoordinatesWrongFilterType() {
        VarHandle intHandle = MemoryHandles.withStride(MemoryLayouts.JAVA_INT.varHandle(int.class), 4);
        MethodHandles.filterCoordinates(intHandle, 1, S2I);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterCoordinatesWrongFilterException() {
        VarHandle intHandle = MemoryHandles.withStride(MemoryLayouts.JAVA_INT.varHandle(int.class), 4);
        MethodHandles.filterCoordinates(intHandle, 1, S2L_EX);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadFilterCoordinatesTooManyFilters() {
        VarHandle intHandle = MemoryHandles.withStride(MemoryLayouts.JAVA_INT.varHandle(int.class), 4);
        MethodHandles.filterCoordinates(intHandle, 1, S2L, S2L);
    }

    @Test
    public void testInsertCoordinates() throws Throwable {
        ValueLayout layout = MemoryLayouts.JAVA_INT;
        MemorySegment segment = MemorySegment.allocateNative(layout);
        VarHandle intHandle = MemoryHandles.withStride(layout.varHandle(int.class), 4);
        VarHandle intHandle_longIndex = MethodHandles.insertCoordinates(intHandle, 0, segment.baseAddress(), 0L);
        intHandle_longIndex.set(1);
        int oldValue = (int)intHandle_longIndex.getAndAdd(42);
        assertEquals(oldValue, 1);
        int value = (int)intHandle_longIndex.get();
        assertEquals(value, 43);
        boolean swapped = (boolean)intHandle_longIndex.compareAndSet(43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_longIndex.compareAndExchange(12, 42);
        assertEquals(oldValue, 12);
        value = (int)intHandle_longIndex.toMethodHandle(VarHandle.AccessMode.GET).invokeExact();
        assertEquals(value, 42);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadInsertCoordinatesNullTarget() {
        MethodHandles.insertCoordinates(null, 0, 42);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadInsertCoordinatesNullValues() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.insertCoordinates(intHandle, 0, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadInsertCoordinatesNegativePos() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.insertCoordinates(intHandle, -1, 42);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadInsertCoordinatesPosTooBig() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.insertCoordinates(intHandle, 1, 42);
    }

    @Test(expectedExceptions = ClassCastException.class)
    public void testBadInsertCoordinatesWrongCoordinateType() {
        VarHandle intHandle = MemoryHandles.withStride(MemoryLayouts.JAVA_INT.varHandle(int.class), 4);
        MethodHandles.insertCoordinates(intHandle, 1, "Hello");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadInsertCoordinatesTooManyValues() {
        VarHandle intHandle = MemoryHandles.withStride(MemoryLayouts.JAVA_INT.varHandle(int.class), 4);
        MethodHandles.insertCoordinates(intHandle, 1, 0L, 0L);
    }

    @Test
    public void testPermuteCoordinates() throws Throwable {
        ValueLayout layout = MemoryLayouts.JAVA_INT;
        MemorySegment segment = MemorySegment.allocateNative(layout);
        VarHandle intHandle = MemoryHandles.withStride(layout.varHandle(int.class), 4);
        VarHandle intHandle_swap = MethodHandles.permuteCoordinates(intHandle,
                List.of(long.class, MemoryAddress.class), 1, 0);
        intHandle_swap.set(0L, segment.baseAddress(), 1);
        int oldValue = (int)intHandle_swap.getAndAdd(0L, segment.baseAddress(), 42);
        assertEquals(oldValue, 1);
        int value = (int)intHandle_swap.get(0L, segment.baseAddress());
        assertEquals(value, 43);
        boolean swapped = (boolean)intHandle_swap.compareAndSet(0L, segment.baseAddress(), 43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_swap.compareAndExchange(0L, segment.baseAddress(), 12, 42);
        assertEquals(oldValue, 12);
        value = (int)intHandle_swap.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(0L, segment.baseAddress());
        assertEquals(value, 42);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadPermuteCoordinatesNullTarget() {
        MethodHandles.permuteCoordinates(null, List.of());
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadPermuteCoordinatesNullCoordinates() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.permuteCoordinates(intHandle, null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadPermuteCoordinatesNullReorder() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.permuteCoordinates(intHandle, List.of(int.class), null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPermuteCoordinatesTooManyCoordinates() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.permuteCoordinates(intHandle, List.of(int.class, int.class), new int[2]);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPermuteCoordinatesTooFewCoordinates() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.permuteCoordinates(intHandle, List.of());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPermuteCoordinatesIndexTooBig() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.permuteCoordinates(intHandle, List.of(int.class, int.class), 3);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadPermuteCoordinatesIndexTooSmall() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.permuteCoordinates(intHandle, List.of(int.class, int.class), -1);
    }

    @Test
    public void testCollectCoordinates() throws Throwable {
        ValueLayout layout = MemoryLayouts.JAVA_INT;
        MemorySegment segment = MemorySegment.allocateNative(layout);
        VarHandle intHandle = MemoryHandles.withStride(layout.varHandle(int.class), 4);
        VarHandle intHandle_sum = MethodHandles.collectCoordinates(intHandle, 1, SUM_OFFSETS);
        intHandle_sum.set(segment.baseAddress(), -2L, 2L, 1);
        int oldValue = (int)intHandle_sum.getAndAdd(segment.baseAddress(), -2L, 2L, 42);
        assertEquals(oldValue, 1);
        int value = (int)intHandle_sum.get(segment.baseAddress(), -2L, 2L);
        assertEquals(value, 43);
        boolean swapped = (boolean)intHandle_sum.compareAndSet(segment.baseAddress(), -2L, 2L, 43, 12);
        assertTrue(swapped);
        oldValue = (int)intHandle_sum.compareAndExchange(segment.baseAddress(), -2L, 2L, 12, 42);
        assertEquals(oldValue, 12);
        value = (int)intHandle_sum.toMethodHandle(VarHandle.AccessMode.GET).invokeExact(segment.baseAddress(), -2L, 2L);
        assertEquals(value, 42);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadCollectCoordinatesNullTarget() {
        MethodHandles.collectCoordinates(null, 0, SUM_OFFSETS);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testBadCollectCoordinatesNullFilters() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.collectCoordinates(intHandle, 0, null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadCollectCoordinatesNegativePos() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.collectCoordinates(intHandle, -1, SUM_OFFSETS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadCollectCoordinatesPosTooBig() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.collectCoordinates(intHandle, 1, SUM_OFFSETS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadCollectCoordinatesWrongFilterType() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.collectCoordinates(intHandle, 0, SUM_OFFSETS);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadCollectCoordinatesWrongVoidFilterType() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.collectCoordinates(intHandle, 0, VOID_FILTER);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadCollectCoordinatesWrongFilterException() {
        VarHandle intHandle = MemoryLayouts.JAVA_INT.varHandle(int.class);
        MethodHandles.collectCoordinates(intHandle, 0, S2L_EX);
    }

    //helper methods

    static int stringToInt(String s) {
        return Integer.valueOf(s);
    }

    static String intToString(int i) {
        return String.valueOf(i);
    }

    static long stringToLong(String s) {
        return Long.valueOf(s);
    }

    static long stringToLongException(String s) throws Throwable {
        return Long.valueOf(s);
    }

    static MemoryAddress baseAddress(MemorySegment segment) {
        return segment.baseAddress();
    }

    static long sumOffsets(long l1, long l2) {
        return l1 + l2;
    }

    static void void_filter(String s) { }
}
