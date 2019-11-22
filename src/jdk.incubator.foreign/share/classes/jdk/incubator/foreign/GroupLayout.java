/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
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
package jdk.incubator.foreign;

import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * A group layout is used to combine together multiple <em>member layouts</em>. There are two ways in which member layouts
 * can be combined: if member layouts are laid out one after the other, the resulting group layout is said to be a <em>struct</em>
 * (see {@link MemoryLayout#ofStruct(MemoryLayout...)}); conversely, if all member layouts are laid out at the same starting offset,
 * the resulting group layout is said to be a <em>union</em> (see {@link MemoryLayout#ofUnion(MemoryLayout...)}).
 *
 * <p>
 * This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; use of identity-sensitive operations (including reference equality
 * ({@code ==}), identity hash code, or synchronization) on instances of
 * {@code GroupLayout} may have unpredictable results and should be avoided.
 * The {@code equals} method should be used for comparisons.
 *
 * @implSpec
 * This class is immutable and thread-safe.
 */
public final class GroupLayout extends AbstractLayout {

    /**
     * The group kind.
     */
    enum Kind {
        /**
         * A 'struct' kind.
         */
        STRUCT(LongStream::sum, Kind::max, "", MH_STRUCT),
        /**
         * A 'union' kind.
         */
        UNION(Kind::max, Kind::max, "|", MH_UNION);

        final ToLongFunction<LongStream> sizeFunc;
        final ToLongFunction<LongStream> alignFunc;
        final String delimTag;
        final MethodHandleDesc mhDesc;

        Kind(ToLongFunction<LongStream> sizeFunc, ToLongFunction<LongStream> alignFunc,
             String delimTag, MethodHandleDesc mhDesc) {
            this.sizeFunc = sizeFunc;
            this.alignFunc = alignFunc;
            this.delimTag = delimTag;
            this.mhDesc = mhDesc;
        }

        static long max(LongStream ls) {
            return ls.max().getAsLong();
        }

        long sizeof(List<MemoryLayout> elems) {
            return sizeFunc.applyAsLong(elems.stream().mapToLong(MemoryLayout::bitSize));
        }

        long alignof(List<MemoryLayout> elems) {
            return alignFunc.applyAsLong(elems.stream().mapToLong(MemoryLayout::bitAlignment));
        }
    }

    private final Kind kind;
    private final List<MemoryLayout> elements;

    GroupLayout(Kind kind, List<MemoryLayout> elements) {
        this(kind, elements, kind.alignof(elements), Optional.empty());
    }

    GroupLayout(Kind kind, List<MemoryLayout> elements, long alignment, Optional<String> name) {
        super(kind.sizeof(elements), alignment, name);
        this.kind = kind;
        this.elements = elements;
    }

    /**
     * Returns the member layouts associated with this group.
     *
     * @apiNote the order in which member layouts are returned is the same order in which member layouts have
     * been passed to one of the group layout factory methods (see {@link MemoryLayout#ofStruct(MemoryLayout...)},
     * {@link MemoryLayout#ofUnion(MemoryLayout...)}).
     *
     * @return the member layouts associated with this group.
     */
    public List<MemoryLayout> memberLayouts() {
        return Collections.unmodifiableList(elements);
    }

    @Override
    public String toString() {
        return decorateLayoutString(elements.stream()
                .map(Object::toString)
                .collect(Collectors.joining(kind.delimTag, "[", "]")));
    }

    /**
     * Is this group layout a <em>struct</em>?
     *
     * @return true, if this group layout is a <em>struct</em>.
     */
    public boolean isStruct() {
        return kind == Kind.STRUCT;
    }

    /**
     * Is this group layout a <em>union</em>?
     *
     * @return true, if this group layout is a <em>union</em>.
     */
    public boolean isUnion() {
        return kind == Kind.UNION;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof GroupLayout)) {
            return false;
        }
        GroupLayout g = (GroupLayout)other;
        return kind.equals(g.kind) && elements.equals(g.elements);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ kind.hashCode() ^ elements.hashCode();
    }

    @Override
    GroupLayout dup(long alignment, Optional<String> name) {
        return new GroupLayout(kind, elements, alignment, name);
    }

    @Override
    public Optional<DynamicConstantDesc<GroupLayout>> describeConstable() {
        ConstantDesc[] constants = new ConstantDesc[1 + elements.size()];
        constants[0] = kind.mhDesc;
        for (int i = 0 ; i < elements.size() ; i++) {
            constants[i + 1] = elements.get(i).describeConstable().get();
        }
        return Optional.of(DynamicConstantDesc.ofNamed(
                    ConstantDescs.BSM_INVOKE, kind.name().toLowerCase(),
                CD_GROUP_LAYOUT, constants));
    }

    //hack: the declarations below are to make javadoc happy; we could have used generics in AbstractLayout
    //but that causes issues with javadoc, see JDK-8224052

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupLayout withName(String name) {
        return (GroupLayout)super.withName(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupLayout withBitAlignment(long alignmentBits) {
        return (GroupLayout)super.withBitAlignment(alignmentBits);
    }
}
