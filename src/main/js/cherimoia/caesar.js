/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.
*/


"use strict";/**
 * @requires cherimoia/skaro
 * @module cherimoia/caesar
 */

import sjs from "cherimoia/skaro";

const VISCHS= " @N/\\Ri2}aP`(xeT4F3mt;8~%r0v:L5$+Z{'V)\"CKIc>z.*" +
              "fJEwSU7juYg<klO&1?[h9=n,yoQGsW]BMHpXb6A|D#q^_d!-",
VISCHS_LEN=  VISCHS.length;

/////////////////////////////////////////////////////////////////////////////
const identifyChar = (pos) => VISCHS.charAt(pos);
const locateChar = (ch) => {
  for (let n= 0; n < VISCHS_LEN; ++n) {
    if (ch === VISCHS.charAt(n)) {
      return n;
    }
  }
  return -1;
}
const slideForward = (delta, cpos) => {
  let ptr= cpos + delta,
  np;
  if (ptr >= VISCHS_LEN) {
    np = ptr - VISCHS_LEN;
  } else {
    np = ptr;
  }
  return identifyChar(np);
}
const slideBack = (delta, cpos) => {
  let ptr= cpos - delta,
  np;
  if (ptr < 0) {
    np= VISCHS_LEN + ptr;
  } else {
    np= ptr;
  }
  return identifyChar(np);
}
const shiftEnc = (shiftpos, delta, cpos) => {
  if (shiftpos < 0) {
    return slideForward( delta, cpos);
  } else {
    return slideBack( delta, cpos);
  }
}
const shiftDec = (shiftpos, delta, cpos) => {
  if ( shiftpos <  0) {
    return slideBack( delta, cpos);
  } else {
    return slideForward( delta, cpos);
  }
}

/** @alias module:cherimoia/caesar */
const xbox = /** @lends xbox# */{
  /**
   * Encrypt the text.
   * @function
   * @param {String} clearText
   * @param {Number} shiftpos
   * @return {String} cipher text
   */
  encrypt(str,shiftpos) {

    if (sjs.isstr(str) && str.length > 0 && shiftpos !== 0) {} else {
      return "";
    }
    const delta = sjs.xmod(Math.abs(shiftpos), VISCHS_LEN),
    out=[],
    len= str.length;
    let p, ch;
    for (let n=0; n < len; ++n) {
      ch = str.charAt(n);
      p= locateChar(ch);
      if (p < 0) {
        //ch
      } else {
        ch= shiftEnc(shiftpos, delta, p);
      }
      out.push(ch);
    }
    return out.join('');
  },

  /**
   * Decrypt the cipher.
   * @function
   * @param {String} cipher
   * @param {Number} shiftpos
   * @return {String} clear text
   */
  decrypt(cipher,shiftpos) {

    if (sjs.isstr(cipher) && cipher.length > 0 && shiftpos !== 0) {} else {
      return "";
    }
    const delta = sjs.xmod(Math.abs(shiftpos),VISCHS_LEN),
    out=[],
    len= cipher.length;
    let p, ch;
    for (let n=0; n < len; ++n) {
      ch= cipher.charAt(n);
      p= locateChar(ch);
      if (p < 0) {
        //ch
      } else {
        ch= shiftDec(shiftpos, delta, p);
      }
      out.push(ch);
    }
    return out.join('');
  }

};

sjs.merge(exports, xbox);
/*@@
return xbox;
@@*/

//////////////////////////////////////////////////////////////////////////////
//EOF

