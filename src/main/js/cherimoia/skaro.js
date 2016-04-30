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
 * @requires global/window
 * @requires console/dbg
 * @requires ramda
 * @requires CryptoJS
 * @module cherimoia/skaro
 */

import global from "global/window";
import DBG from "console/dbg";
import R from "ramda";

const fnTest = /xyz/.test(function(){xyz;}) ? /\b_super\b/ : /[\D|\d]*/,
ZEROS= "00000000000000000000000000000000";  //32
let CjsBase64,
CjsUtf8,
undef;

if (typeof HTMLElement === 'undefined') {
  // fake a type.
  global.HTMLElement= function HTMLElement() {};
}

if (typeof CryptoJS !== 'undefined') {
  global.CryptoJS= CryptoJS;
}

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//
let _echt = (obj) => { return typeof obj !== 'undefined' && obj !== null; }

if (_echt( global.CryptoJS))  {
  CjsBase64= global.CryptoJS.enc.Base64;
  CjsUtf8= global.CryptoJS.enc.Utf8;
}

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// js inheritance - lifted from impact.js
//----------------------------------------------------------------------------
/** * @private */
function _patchProto(zuper, proto, other) {
  let par={},
  name;
  for (name in other) {
    if (typeof(zuper[name]) === "function" &&
        typeof(other[name]) === "function" &&
        fnTest.test(other[name])) {
      par[name] = zuper[name]; // save original function
      proto[name] = ((name, fn) => {
        return function() {
          let tmp = this._super,
          ret;
          this._super = par[name];
          ret = fn.apply(this, arguments);
          this._super = tmp;
          return ret;
        };
      })(name, other[name]);
    } else {
      proto[name] = other[name];
    }
  }
}

let wrapper= function() {},
initing = false,
_mixer = function (other) {
  let proto;

  initing = true; proto = new this(); initing = false;
  _patchProto(this.prototype, proto, other);

  function claxx() {
    if ( !initing && !!this.ctor) {
      this.ctor.apply(this, arguments);
    }
    return this;
  }

  claxx.prototype = proto;
  claxx.prototype.constructor = Claxx;
  claxx.mixes = _mixer;
  claxx.patch = function(other) {
    _patchProto(this.prototype,
                this.prototype, other);
  };

  return claxx;
};

//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/** @alias module:cherimoia/skaro */
const xbox = /** @lends xbox# */{
/*
  strPadRight(str,len, pad){
    return (str+new Array(len+1).join(pad)).slice(0,len);
  },
  strPadLeft(str,len,pad){
    return (new Array(len+1).join(pad)+str).slice(-len);
  },
*/
  /**
   * Maybe pad a string (right side.)
   * @function
   * @param {String} str
   * @param {Number} len
   * @param {String} s
   * @return {String}
   */
  strPadRight(str, len, s) {
    return (len -= str.length) > 0
    ? str + new Array(Math.ceil(len/s.length) + 1).join(s).substr(0, len)
    : str;
  },

  /**
   * Maybe pad a string (left side.)
   * @function
   * @param {String} str
   * @param {Number} len
   * @param {String} s
   * @return {String}
   */
  strPadLeft(str, len, s) {
    return (len -= str.length) > 0
    ? new Array(Math.ceil(len/s.length) + 1).join(s).substr(0, len) + str
    : str;
  },

  /**
   * Safely split a string, null and empty strings are removed.
   * @function
   * @param {String} s
   * @param {String} sep
   * @return {Array.String}
   */
  safeSplit(s, sep) {
    return !!s ? R.reject(function(z) { return z.length===0; }, s.trim().split(sep)) : [];
  },

  /**
   * Get the current time.
   * @function
   * @return {Number} time in milliseconds.
   */
  now: Date.now || function() { return new Date().getTime(); },

  /**
   * Capitalize the first char of the string.
   * @function
   * @param {String} str
   * @return {String} with the first letter capitalized.
   */
  capitalize(str) {
    return str.charAt(0).toUpperCase() + str.slice(1);
  },

  /**
   * Pick a random number between these 2 limits.
   * @function
   * @param {Number} from
   * @param {Number} to
   * @return {Number}
   */
  randRange(from, to) {
    return Math.floor(Math.random() * (to - from + 1) + from);
  },

  /**
   * Return the proper mathematical modulo of x mod N.
   * @function
   * @param {Number} x
   * @param {Number} N
   * @return {Number}
   */
  xmod(x, N) {
    if (x < 0) {
      return x - (-1 * (Math.floor(-x / N) * N + N));
    } else {
      return x % N;
    }
  },

  /**
   * Create an array of len, seeding it with value.
   * @function
   * @param {Number} len
   * @param {Object} value
   * @return {Array.Any}
   */
  makeArray(len, value) {
    const arr=[];
    for (let n=0; n < len; ++n) { arr.push(value); }
    return arr;
  },

  /**
   * Throw an error exception.
   * @function
   * @param {String} msg
   */
  tne(msg) { throw new Error(msg); },

  /**
   * A no-op function.
   * @function
   */
  NILFUNC() {},

  /**
   * Test if object is valid and not null.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  echt: _echt,

  /**
   * Maybe pad the number with zeroes.
   * @function
   * @param {Number} num
   * @param {Number} digits
   * @return {String}
   */
  prettyNumber(num, digits) {
    return this.strPadLeft(Number(num).toString(), digits, "0");
    /*
    var nums= Number(num).toString(),
    len= nums.length;
    if (digits > 32) { throw new Error("Too many digits to prettify."); }
    var s= ZEROS.substring(0,digits);
    if (len < digits) {
      return s.substring(0, digits - len)  + nums;
    } else {
      return nums;
    }
    */
  },

  /**
   * Get the websocket transport protocol.
   * @function
   * @return {String} transport protocol for websocket
   */
  getWebSockProtocol() {
    return this.isSSL() ? "wss://" : "ws://";
  },

  /**
   * Get the current time in milliseconds.
   * @function
   * @return {Number} current time (millisecs)
   */
  nowMillis() {
    return this.now();
  },

  /**
   * Cast the value to boolean.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  boolify(obj) {
    return obj ? true : false;
  },

  /**
   * Remove some arguments from the front.
   * @function
   * @param {Javascript.arguments} args
   * @param {Number} num
   * @return {Array} remaining arguments
   */
  dropArgs(args,num) {
    return args.length > num ? Array.prototype.slice(args,num) : [];
  },

  /**
   * Returns true if the web address is ssl.
   * @function
   * @return {Boolean}
   */
  isSSL() {
    if (!!window && window.location) {
      return window.location.protocol.indexOf('https') >= 0;
    } else {
      return false;
    }
  },

  /**
   * Format a URL based on the current web address host.
   * @function
   * @param {String} scheme
   * @param {String} uri
   * @return {String}
   */
  fmtUrl(scheme, uri) {
    if (!!window && window.location) {
      return scheme + window.location.host + uri;
    } else {
      return "";
    }
  },

  /**
   * @function
   * @param {String} s
   * @return {Object}
   */
  objectfy(s) {
    return !!s ? JSON.parse(s) : null;
  },

  /**
   * @function
   * @param {Object} obj
   * @return {String}
   */
  jsonfy(obj) {
    return !!obj ? JSON.stringify(obj) : null;
  },

  /**
   * Test if the client is a mobile device.
   * @function
   * @param {String} navigator
   * @return {Boolean}
   */
  isMobile(navigator) {
    if (!!navigator) {
      return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
    } else {
      return false;
    }
  },

  /**
   * Test if the client is Safari browser.
   * @function
   * @param {String} navigator
   * @return {Boolean}
   */
  isSafari(navigator) {
    if (!!navigator) {
      return /Safari/.test(navigator.userAgent) && /Apple Computer/.test(navigator.vendor);
    } else {
      return false;
    }
  },

  /**
   * Prevent default propagation of this event.
   * @function
   * @param {Event} e
   */
  pde(e) {
    if (!!e.preventDefault) {
      e.preventDefault();
    } else {
      e.returnValue = false;
    }
  },

  /**
   * Randomly pick positive or negative.
   * @function
   * @return {Number}
   */
  randSign() {
    if (this.rand(10) % 2 === 0) {
      return -1;
    } else {
      return 1;
    }
  },

  /**
   * Randomly choose an item from this array.
   * @function
   * @param {Array} arr
   * @return {Object}
   */
  randArrayItem(arr) {
    return arr.length === 0 ?
      null :
      arr.length === 1 ?
      arr[0] :
      arr[ Math.floor(Math.random() * arr.length) ];
  },

  /**
   * Randomly choose a percentage in step of 10.
   * @function
   * @return {Number}
   */
  randPercent() {
    const pc = [0.1,0.9,0.3,0.7,0.6,0.5,0.4,0.8,0.2];
    return this.randArrayItem(pc);
  },

  /**
   * Pick a random number.
   * @function
   * @param {Number} limit
   * @return {Number}
   */
  rand(limit) {
    return Math.floor(Math.random() * limit);
  },

  /**
   * Format input into HTTP Basic Authentication.
   * @function
   * @param {String} user
   * @param {String} pwd
   * @return {Array.String} - [header, data]
   */
  toBasicAuthHeader(user,pwd) {
    const str='Basic ' + this.base64_encode(""+user+":"+pwd);
    return [ 'Authorization', str ];
  },

  /**
   * Convert string to utf-8 string.
   * @function
   * @param {String} s
   * @return {String}
   */
  toUtf8(s) {
    return CjsUtf8.stringify( CjsUtf8.parse(s));
  },

  /**
   * Base64 encode the string.
   * @function
   * @param {String} s
   * @return {String}
   */
  base64_encode: function(s) {
    return CjsBase64.stringify( CjsUtf8.parse(s));
  },

  /**
   * Base64 decode the string.
   * @function
   * @param {String} s
   * @return {String}
   */
  base64_decode(s) {
    return CjsUtf8.stringify( CjsBase64.parse(s));
  },

  /**
   * Merge 2 objects together.
   * @function
   * @param {Object} original
   * @param {Object} extended
   * @return {Object} a new object
   */
  mergeEx(original,extended) {
    return this.merge(this.merge({},original), extended);
  },

  /**
   * Merge 2 objects in place.
   * @function
   * @param {Object} original
   * @param {Object} extended
   * @return {Object} the modified original object
   */
  merge(original, extended) {
    let key, ext;
    for(key in extended) {
      ext = extended[key];
      if ( ext instanceof xbox.ES6Claxx ||
           ext instanceof HTMLElement ||
           typeof(ext) !== 'object' ||
           ext === null ||
           !original[key]) {
        original[key] = ext;
      } else {
        if (typeof(original[key]) !== 'object' ) {
          original[key] = (ext instanceof Array) ? [] : {};
        }
        this.merge( original[key], ext );
      }
    }
    return original;
  },

  /**
   * Maybe remove this item from this array.
   * @function
   * @return {Array}
   */
  removeFromArray(arr, item) {
    let index = arr.indexOf(item);
    while (index !== -1) {
      arr.splice(index,1);
      index = arr.indexOf(item);
    }
    return arr;
  },

  /**
   * Test if the input is *undefined*.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isundef(obj) {
    return obj === void 0;
  },

  /**
   * Test if input is null.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isnull(obj) {
    return obj === null;
  },

  /**
   * Test if input is a Number.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isnum(obj) {
    return toString.call(obj) === '[object Number]';
  },

  /**
   * Test if input is a Date.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isdate(obj) {
    return toString.call(obj) === '[object Date]';
  },

  /**
   * Test if input is a Function.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isfunc(obj) {
    return toString.call(obj) === '[object Function]';
  },

  /**
   * Test if input is a String.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isstr(obj) {
    return toString.call(obj) === '[object String]';
  },

  /**
   * Test if input is an Array.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isarr(obj) {
    return !!obj && toString.call(obj) === '[object Array]';
  },

  /**
   * Test if input is an Object.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isobj(obj) {
    const type = typeof obj;
    return type === 'function' || type === 'object' && !!obj;
  },

  /**
   * Test if input has *length* attribute, and if so, is it
   * empty.
   * @function
   * @param {Object} obj
   * @return {Boolean}
   */
  isempty(obj) {
    if (this.isobj(obj)) {
      return Object.keys(obj).length === 0;
    }

    if (!!obj && typeof obj.length === 'number') {
      return obj.length === 0;
    }

    return false;
  },

  /**
   * Test if this object has this key.
   * @function
   * @param {Object} obj
   * @param {Object} key
   * @return {Boolean}
   */
  hasKey(obj, key) {
    return !!obj && Object.prototype.hasOwnProperty.call(obj, key);
  },

  //since R doesn't handle object :(
  /**
   * Perform reduce on this object.
   * @function
   * @param {Function} f
   * @param {Object} memo
   * @param {Object} obj
   * @return {Object}  memo
   */
  reduceObj(f, memo, obj) {
    return R.reduce(function(sum, pair) {
      return f(sum, pair[1], pair[0]);
    },
    memo,
    R.toPairs(obj));
  },

  /**
   * Iterate over this object [k,v] pairs and call f(v,k).
   * @function
   * @param {Function} f
   * @param {Object} obj
   * @return {Object} original object
   */
  eachObj(f, obj) {
    R.forEach(function(pair) {
      return f(pair[1], pair[0]);
    },
    R.toPairs(obj));
    return obj;
  },

  /**
   * Mixin this object.
   * @function
   * @param {Object} object
   * @return {Object}
   */
  mixes(obj) {
    return _mixer(obj);
  },

  /**
   * @property {Logger} logger Short cut to logger
   */
  logger: DBG,

  /**
   * @property {Logger} loggr Short cut to logger
   */
  loggr: DBG,

  /**
   * @property {Ramda} ramda Short cut to Ramda
   */
  ramda: R,

  /**
   * @property {Ramda} R Short cut to Ramda
   */
  R: R,

  /**
   * @property {Claxx} Claxx ES6 Class
   */
  ES6Claxx: class ES6Claxx {}

};

xbox.merge(exports, xbox);
/*@@
return xbox;
@@*/

//////////////////////////////////////////////////////////////////////////////
//EOF

