/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.clang;

import java.foreign.NativeTypes;
import java.foreign.Scope;
import java.foreign.memory.LayoutType;
import java.foreign.memory.Pointer;
import java.foreign.memory.Array;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import clang.CXString_h.CXString;
import clang.Index_h.CXDiagnostic;
import clang.Index_h.CXToken;
import clang.Index_h.CXTokenKind;
import clang.Index_h.CXTranslationUnitImpl;
import clang.Index_h.CXUnsavedFile;

public class TranslationUnit {
    final Pointer<CXTranslationUnitImpl> tu;
    private final Scope scope = Scope.globalScope().fork();

    TranslationUnit(Pointer<CXTranslationUnitImpl> tu) {
        this.tu = tu;
    }

    public Cursor getCursor() {
        return new Cursor(LibClang.lib.clang_getTranslationUnitCursor(tu));
    }

    public Diagnostic[] getDiagnostics() {
        final clang.Index_h lclang = LibClang.lib;

        int cntDiags = lclang.clang_getNumDiagnostics(tu);
        Diagnostic[] rv = new Diagnostic[cntDiags];
        for (int i = 0; i < cntDiags; i++) {
            @CXDiagnostic Pointer<Void> diag = lclang.clang_getDiagnostic(tu, i);
            rv[i] = new Diagnostic(diag);
        }

        return rv;
    }

    public final void save(Path path) throws TranslationUnit.TranslationUnitSaveException {
        try (Scope sc = Scope.globalScope().fork()) {
            int res = LibClang.lib.clang_saveTranslationUnit(tu,
                    sc.allocateCString(path.toAbsolutePath().toString()), 0);
            if (res != 0) {
                throw new TranslationUnit.TranslationUnitSaveException(path);
            }
        }
    }

    void processDiagnostics(Consumer<Diagnostic> dh) {
        Objects.requireNonNull(dh);
        for (Diagnostic diag : getDiagnostics()) {
            dh.accept(diag);
        }
    }

    public int reparse(Index.UnsavedFile... inMemoryFiles) {
        final clang.Index_h lclang = LibClang.lib;

        try (Scope s = Scope.globalScope().fork()) {
            Array<CXUnsavedFile> files = inMemoryFiles.length == 0 ? null :
                    s.allocateArray(LayoutType.ofStruct(CXUnsavedFile.class), inMemoryFiles.length);
            for (int i = 0; i < inMemoryFiles.length; i++) {
                files.get(i).Filename$set(s.allocateCString(inMemoryFiles[i].file));
                files.get(i).Contents$set(s.allocateCString(inMemoryFiles[i].contents));
                files.get(i).Length$set(inMemoryFiles[i].contents.length());
            }
            return lclang.clang_reparseTranslationUnit(tu, inMemoryFiles.length,
                    files == null ? Pointer.ofNull() : files.elementPointer(),
                    lclang.clang_defaultReparseOptions(tu));
        }
    }

    public void reparse(Consumer<Diagnostic> dh, Index.UnsavedFile... inMemoryFiles) {
        reparse(inMemoryFiles);
        processDiagnostics(dh);
    }

    public String[] tokens(SourceRange range) {
        Tokens tokens = tokenize(range);
        String rv[] = new String[tokens.size()];
        for (int i = 0; i < rv.length; i++) {
            rv[i] = tokens.getToken(i).spelling();
        }
        return rv;
    }

    public Tokens tokenize(SourceRange range) {
        // FIXME: Use a shared scope now, we really want to use is StackScope
        Pointer<Pointer<CXToken>> p = scope.allocate(LayoutType.ofStruct(CXToken.class).pointer());
        Pointer<Integer> pCnt = scope.allocate(NativeTypes.UINT);
        LibClang.lib.clang_tokenize(tu, range.range, p, pCnt);
        Tokens rv = new Tokens(p.get().cast(LayoutType.ofStruct(CXToken.class)), pCnt.get());
        return rv;
    }

    public void dispose() {
        LibClang.lib.clang_disposeTranslationUnit(tu);
    }

    public class Tokens {
        private final Pointer<CXToken> ar;
        private final int size;

        Tokens(Pointer<CXToken> ar, int size) {
            this.ar = ar;
            this.size = size;
        }

        public void dispose() {
            LibClang.lib.clang_disposeTokens(tu, ar, size);
        }

        public int size() {
            return size;
        }

        public Token getToken(int idx) {
            Pointer<CXToken> p = ar.offset(idx);
            return new Token(p.get());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < size; i++) {
                CXString s = LibClang.lib.clang_getTokenSpelling(tu, ar.offset(i).get());
                sb.append("Token[");
                sb.append(i);
                sb.append("]=");
                sb.append(LibClang.CXStrToString(s));
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public class Token {
        final CXToken token;

        Token(CXToken token) {
            this.token = token;
        }

        public @CXTokenKind int kind() {
            return LibClang.lib.clang_getTokenKind(token);
        }

        public String spelling() {
            CXString s = LibClang.lib.clang_getTokenSpelling(
                    tu, token);
            return LibClang.CXStrToString(s);
        }

        public SourceLocation getLocation() {
            return new SourceLocation(LibClang.lib.clang_getTokenLocation(
                    tu, token));
        }

        public SourceRange getExtent() {
            return new SourceRange(LibClang.lib.clang_getTokenExtent(
                    tu, token));
        }
    }

    public static class TranslationUnitSaveException extends IOException {

        static final long serialVersionUID = 1L;

        TranslationUnitSaveException(Path path) {
            super("Cannot save translation unit to: " + path.toAbsolutePath());
        }
    }
}
