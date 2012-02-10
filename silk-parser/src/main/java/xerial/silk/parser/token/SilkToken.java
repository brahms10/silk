/*
 * Copyright 2012 Taro L. Saito
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//--------------------------------------
// XerialJ
//
// SilkToken.java
// Since: 2011/05/02 8:51:37
//
// $URL$
// $Author$
//--------------------------------------
package xerial.silk.parser.token;

import xerial.silk.parser.SilkLexer;

/**
 * Token generated by {@link SilkLexer}
 * 
 * @author leo
 * 
 */
public class SilkToken
{
    public final SilkTokenType type;
    public final int           posInLine;

    public SilkToken(SilkTokenType type, int posInLine) {
        this.type = type;
        this.posInLine = posInLine;
    }

    public CharSequence getText() {
        return type.toRawString();
    }

    @SuppressWarnings("unchecked")
    public <T extends SilkToken> T cast() {
        return (T) this;
    }

    @Override
    public String toString() {
        return String.format("pos:%2d [%s] %s", posInLine, type.name(), getText());
    }
}
