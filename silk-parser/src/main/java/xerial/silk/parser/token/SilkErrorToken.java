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
// SilkErrorToken.java
// Since: 2011/06/06 11:16:35
//
// $URL$
// $Author$
//--------------------------------------
package xerial.silk.parser.token;

/**
 * Emitted when an error is found
 * 
 * @author leo
 * 
 */
public class SilkErrorToken extends SilkTextToken
{
    public final String errorMessage;

    public SilkErrorToken(int posInLine, CharSequence lineText, String errorMessage) {
        super(SilkTokenType.Error, lineText, posInLine);
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        StringBuilder caret = new StringBuilder();
        for (int i = 0; i < posInLine; ++i)
            caret.append(' ');
        caret.append('^');

        return String.format("pos:%d %s\n%s\n%s", posInLine, errorMessage, chompNewLine(text.toString()),
                caret.toString());
    }
}
