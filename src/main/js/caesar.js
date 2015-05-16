// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013-2015 Ken Leung. All rights reserved.

/**
 * @requires cherimoia/skarojs
 * @module cherimoia/caesar
 */
define("cherimoia/caesar",

  ['cherimoia/skarojs'],

  function (sjs) { "use strict";

    var VISCHS= " @N/\\Ri2}aP`(xeT4F3mt;8~%r0v:L5$+Z{'V)\"CKIc>z.*" +
                "fJEwSU7juYg<klO&1?[h9=n,yoQGsW]BMHpXb6A|D#q^_d!-",
    VISCHS_LEN=  VISCHS.length;

    /////////////////////////////////////////////////////////////////////////////
    //
    function identifyChar(pos) { return VISCHS.charAt(pos); }
    function locateChar(ch) {
      var n;
      for (n= 0; n < VISCHS_LEN; ++n) {
        if (ch === VISCHS.charAt(n)) {
          return n;
        }
      }
      return -1;
    }
    function slideForward(delta, cpos) {
      var np, ptr= cpos + delta;
      if (ptr >= VISCHS_LEN) {
        np = ptr - VISCHS_LEN;
      } else {
        np = ptr;
      }
      return identifyChar(np);
    }
    function slideBack(delta, cpos) {
      var np, ptr= cpos - delta;
      if (ptr < 0) {
        np= VISCHS_LEN + ptr;
      } else {
        np= ptr;
      }
      return identifyChar(np);
    }
    function shiftEnc( shiftpos, delta, cpos) {
      if (shiftpos < 0) {
        return slideForward( delta, cpos);
      } else {
        return slideBack( delta, cpos);
      }
    }
    function shiftDec( shiftpos, delta, cpos) {
      if ( shiftpos <  0) {
        return slideBack( delta, cpos);
      } else {
        return slideForward( delta, cpos);
      }
    }

    /** @alias module:cherimoia/caesar */
    var exports = {
      /**
       * Encrypt the text.
       *
       * @method encrypt
       * @static
       * @param {String} clearText
       * @param {Number} shiftpos
       * @return {String} cipher text
       */
      encrypt: function (str,shiftpos) {

        if (sjs.isString(str) && str.length > 0 && shiftpos !== 0) {} else {
          return "";
        }
        var delta = sjs.xmod(Math.abs(shiftpos), VISCHS_LEN);
        var p, ch, n, len= str.length;
        var out=[];
        for (n=0; n < len; ++n) {
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
       *
       * @method decrypt
       * @static
       * @param {String} cipherText
       * @param {Number} shiftpos
       * @return {String} clear text
       */
      decrypt: function (cipherText,shiftpos) {

        if (sjs.isString(cipherText) && cipherText.length > 0 && shiftpos !== 0) {} else {
          return "";
        }
        var delta = sjs.xmod(Math.abs(shiftpos),VISCHS_LEN);
        var ch, n, len= cipherText.length;
        var p, out=[];
        for (n=0; n < len; ++n) {
          ch= cipherText.charAt(n);
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

    return exports;
});

//////////////////////////////////////////////////////////////////////////////
//EOF

