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
 * @requires global/window
 * @requires console/dbg
 * @requires ramda
 * @requires CryptoJS
 * @module cherimoia/skarojs
 */
define("cherimoia/skarojs",

       ['global/window',
        'console/dbg',
        'ramda'],

  function (global,DBG,R) { "use strict";

    var fnTest = /xyz/.test(function(){xyz;}) ? /\b_super\b/ : /[\D|\d]*/,
    ZEROS= "00000000000000000000000000000000",  //32
    CjsBase64,
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
    function _echt (obj) {
      return typeof obj !== 'undefined' && obj !== null;
    }

    if (_echt( global.CryptoJS))  {
      CjsBase64= global.CryptoJS.enc.Base64;
      CjsUtf8= global.CryptoJS.enc.Utf8;
    }

    //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // js inheritance - lifted from impact.js
    //----------------------------------------------------------------------------
    /**
     * @private
     */
    function patchProto(zuper, proto, other) {
      var par={},
      name;

      for (name in other) {
        if (typeof(zuper[name]) === "function" &&
            typeof(other[name]) === "function" &&
            fnTest.test(other[name])) {
          par[name] = zuper[name]; // save original function
          proto[name] = (function(name, fn){
            return function() {
              var tmp = this._super;
              this._super = par[name];
              var ret = fn.apply(this, arguments);
              this._super = tmp;
              return ret;
            };
          })(name, other[name]);
        } else {
          proto[name] = other[name];
        }
      }
    }

    var monkeyPatch = function(other) {
      patchProto(this.prototype,
                 this.prototype, other);
    },
    klass= function() {},
    initing = false;

    // inheritance method
    klass.mixes = function (other) {
      var proto;

      initing = true; proto = new this(); initing = false;
      patchProto(this.prototype, proto, other);

      function Claxx() {
        if ( !initing ) {
          // static constructor?
          if (!!this.staticCtor) {
            var obj = this.staticCtor.apply(this, arguments);
            if (!!obj) { return obj; }
          }
          if (!!this.ctor) {
            this.ctor.apply(this, arguments);
          }
        }
        return this;
      }

      Claxx.prototype = proto;
      Claxx.prototype.constructor = Claxx;
      Claxx.mixes = klass.mixes;
      Claxx.inject = monkeyPatch;

      return Claxx;
    };

    //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    /** @alias module:cherimoia/skarojs */
    var exports = {
/*
      strPadRight: function(str,len, pad){
        return (str+new Array(len+1).join(pad)).slice(0,len);
      },

      strPadLeft: function(str,len,pad){
        return (new Array(len+1).join(pad)+str).slice(-len);
      },
*/
      /**
       * Maybe pad a string (right side.)
       *
       * @method strPadRight
       * @static
       * @param {String} str
       * @param {Number} len
       * @param {String} s
       * @return {String}
       */
      strPadRight: function(str, len, s) {
        return (len -= str.length) > 0
        ? str + new Array(Math.ceil(len/s.length) + 1).join(s).substr(0, len)
        : str;
      },

      /**
       * Maybe pad a string (left side.)
       *
       * @method strPadLeft
       * @static
       * @param {String} str
       * @param {Number} len
       * @param {String} s
       * @return {String}
       */
      strPadLeft: function(str, len, s) {
        return (len -= str.length) > 0
        ? new Array(Math.ceil(len/s.length) + 1).join(s).substr(0, len) + str
        : str;
      },

      /**
       * Safely split a string, null and empty strings are removed.
       *
       * @method safeSplit
       * @static
       * @param {String} s
       * @param {String} sep
       * @return {Array.String}
       */
      safeSplit: function(s, sep) {
        return !!s ? R.reject(function(z) { return z.length===0; }, s.trim().split(sep)) : [];
      },

      /**
       * Get the current time.
       *
       * @method now
       * @static
       * @return {Number} time in milliseconds.
       */
      now: Date.now || function() { return new Date().getTime(); },

      /**
       * Capitalize the first char of the string.
       *
       * @method capitalize
       * @static
       * @param {String} str
       * @return {String} with the first letter capitalized.
       */
      capitalize: function(str) {
        return str.charAt(0).toUpperCase() + str.slice(1);
      },

      /**
       * Pick a random number between these 2 limits.
       *
       * @method randomRange
       * @static
       * @param {Number} from
       * @param {Number} to
       * @return {Number}
       */
      randomRange: function(from, to) {
        return Math.floor(Math.random() * (to - from + 1) + from);
      },

      /**
       * Return the proper mathematical modulo of x mod N.
       *
       * @method xmod
       * @static
       * @param {Number} x
       * @param {Number} N
       * @return {Number}
       */
      xmod: function(x, N) {
        if (x < 0) {
          return x - (-1 * (Math.floor(-x / N) * N + N));
        } else {
          return x % N;
        }
      },

      /**
       * Create an array of len, seeding it with value.
       *
       * @method makeArray
       * @static
       * @param {Number} len
       * @param {Object} value
       * @return {Array}
       */
      makeArray: function(len, value) {
        var n, arr=[];
        for (n=0; n < len; ++n) { arr.push(value); }
        return arr;
      },

      /**
       * Throw an error exception.
       *
       * @method tne
       * @static
       * @param {String} msg
       */
      tne: function(msg) { throw new Error(msg); },

      /**
       * A no-op function.
       *
       * @method NILFUNC
       * @static
       */
      NILFUNC: function() {},

      /**
       * Test if object is valid and not null.
       *
       * @method echt
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      echt: _echt,

      /**
       * Maybe pad the number with zeroes.
       *
       * @method prettyNumber
       * @static
       * @param {Number} num
       * @param {Number} digits
       * @return {String}
       */
      prettyNumber: function (num, digits) {
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
       *
       * @method getWebSockProtocol
       * @static
       * @return {String} the transport protocol for websocket
       */
      getWebSockProtocol: function() {
        return this.isSSL() ? "wss://" : "ws://";
      },

      /**
       * Get the current time in milliseconds.
       *
       * @method nowMillis
       * @static
       * @return {Number} current time (millisecs)
       */
      nowMillis: function() {
        return this.now();
      },

      /**
       * Cast the value to boolean.
       *
       * @method boolify
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      boolify: function(obj) {
        return obj ? true : false;
      },

      /**
       * Remove some arguments from the front.
       *
       * @method dropArgs
       * @static
       * @param {Javascript.arguments} args
       * @param {Number} num
       * @return {Array} remaining arguments
       */
      dropArgs: function(args,num) {
        return args.length > num ? Array.prototype.slice(args,num) : [];
      },

      /**
       * Returns true if the web address is ssl.
       *
       * @method isSSL
       * @static
       * @return {Boolean}
       */
      isSSL: function() {
        if (!!window && window.location) {
          return window.location.protocol.indexOf('https') >= 0;
        } else {
          return false;
        }
      },

      /**
       * Format a URL based on the current web address host.
       *
       * @method fmtUrl
       * @static
       * @param {String} scheme
       * @param {String} uri
       * @return {String}
       */
      fmtUrl: function (scheme, uri) {
        if (!!window && window.location) {
          return scheme + window.location.host + uri;
        } else {
          return "";
        }
      },

      /**
       * @method jsonDecode
       * @static
       * @param {String} s
       * @return {Object}
       */
      jsonDecode: function(s) {
        return !!s ? JSON.parse(s) : null;
      },

      /**
       * @method jsonEncode
       * @static
       * @param {Object} obj
       * @return {String}
       */
      jsonEncode: function(obj) {
        return !!obj ? JSON.stringify(obj) : null;
      },

      /**
       * Test if the client is a mobile device.
       *
       * @method isMobile
       * @static
       * @param {String} navigator
       * @return {Boolean}
       */
      isMobile: function (navigator) {
        if (!!navigator) {
          return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
        } else {
          return false;
        }
      },

      /**
       * Test if the client is Safari browser.
       *
       * @method isSafari
       * @static
       * @param {String} navigator
       * @return {Boolean}
       */
      isSafari: function(navigator) {
        if (!!navigator) {
          return /Safari/.test(navigator.userAgent) && /Apple Computer/.test(navigator.vendor);
        } else {
          return false;
        }
      },

      /**
       * Prevent default propagation of this event.
       *
       * @method pde
       * @static
       * @param {Event} e
       */
      pde: function (e) {
        if (!!e.preventDefault) {
          e.preventDefault();
        } else {
          e.returnValue = false;
        }
      },

      /**
       * Randomly pick positive or negative.
       *
       * @method randSign
       * @static
       * @return {Number}
       */
      randSign: function() {
        if (this.rand(10) % 2 === 0) {
          return -1;
        } else {
          return 1;
        }
      },

      /**
       * Randomly choose an item from this array.
       *
       * @method randArrayItem
       * @static
       * @param {Array} arr
       * @return {Object}
       */
      randArrayItem: function(arr) {
        return arr.length === 0 ? null : arr.length === 1 ? arr[0] : arr[ Math.floor(Math.random() * arr.length) ];
      },

      /**
       * Randomly choose a percentage in step of 10.
       *
       * @method randPercent
       * @static
       * @return {Number}
       */
      randPercent: function() {
        var pc = [0.1,0.9,0.3,0.7,0.6,0.5,0.4,0.8,0.2];
        return this.randArrayItem(pc);
      },

      /**
       * Pick a random number.
       *
       * @method rand
       * @static
       * @param {Number} limit
       * @return {Number}
       */
      rand: function(limit) {
        return Math.floor(Math.random() * limit);
      },

      /**
       * Format input into HTTP Basic Authentication.
       *
       * @method toBasicAuthHeader
       * @static
       * @param {String} user
       * @param {String} pwd
       * @return {Array.String} - [header, data]
       */
      toBasicAuthHeader: function(user,pwd) {
        var str='Basic ' + this.base64_encode(""+user+":"+pwd);
        return [ 'Authorization', str ];
      },

      /**
       * Convert string to utf-8 string.
       *
       * @method toUtf8
       * @static
       * @param {String} s
       * @return {String}
       */
      toUtf8: function(s) {
        return CjsUtf8.stringify( CjsUtf8.parse(s));
      },

      /**
       * Base64 encode the string.
       *
       * @method base64_encode
       * @static
       * @param {String} s
       * @return {String}
       */
      base64_encode: function(s) {
        return CjsBase64.stringify( CjsUtf8.parse(s));
      },

      /**
       * Base64 decode the string.
       *
       * @method base64_decode
       * @static
       * @param {String} s
       * @return {String}
       */
      base64_decode: function(s) {
        return CjsUtf8.stringify( CjsBase64.parse(s));
      },

      /**
       * Merge 2 objects together.
       *
       * @method mergeEx
       * @static
       * @param {Object} original
       * @param {Object} extended
       * @return {Object} a new object
       */
      mergeEx:function(original,extended) {
        return this.merge(this.merge({},original), extended);
      },

      /**
       * Merge 2 objects in place.
       *
       * @method merge
       * @static
       * @param {Object} original
       * @param {Object} extended
       * @return {Object} the modified original object
       */
      merge: function(original, extended) {
        var key, ext;
        for(key in extended) {
          ext = extended[key];
          if ( typeof(ext) !== 'object' ||
               ext instanceof klass ||
               ext instanceof HTMLElement ||
               ext === null ) {
            original[key] = ext;
          } else {
            if( !original[key] || typeof(original[key]) !== 'object' ) {
              original[key] = (ext instanceof Array) ? [] : {};
            }
            this.merge( original[key], ext );
          }
        }
        return original;
      },

      /**
       * Maybe remove this item from this array.
       *
       * @method removeFromArray
       * @static
       * @return {Array}
       */
      removeFromArray: function(arr, item) {
        if (arr && arr.indexOf && arr.splice) {
          var index = arr.indexOf(item);
          while (index !== -1) {
            arr.splice(index,1);
            index = arr.indexOf(item);
          }
        }
        return arr;
      },

      /**
       * Test if the input is *undefined*.
       *
       * @method isUndef
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isUndef: function(obj) {
        return obj === void 0;
      },

      /**
       * Test if input is null.
       *
       * @method isNull
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isNull: function(obj) {
        return obj === null;
      },

      /**
       * Test if input is a Number.
       *
       * @method isNumber
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isNumber: function(obj) {
        return toString.call(obj) === '[object Number]';
      },

      /**
       * Test if input is a Date.
       *
       * @method isDate
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isDate: function(obj) {
        return toString.call(obj) === '[object Date]';
      },

      /**
       * Test if input is a Function.
       *
       * @method isFunction
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isFunction: function(obj) {
        return toString.call(obj) === '[object Function]';
      },

      /**
       * Test if input is a String.
       *
       * @method isString
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isString: function(obj) {
        return toString.call(obj) === '[object String]';
      },

      /**
       * Test if input is an Array.
       *
       * @method isArray
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isArray: function(obj) {
        return !!obj && toString.call(obj) === '[object Array]';
      },

      /**
       * Test if input is an Object.
       *
       * @method isObject
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isObject: function(obj) {
        var type = typeof obj;
        return type === 'function' || type === 'object' && !!obj;
      },

      /**
       * Test if input has *length* attribute, and if so, is it
       * empty.
       *
       * @method isEmpty
       * @static
       * @param {Object} obj
       * @return {Boolean}
       */
      isEmpty: function(obj) {
        if (this.isObject(obj)) {
          return Object.keys(obj).length === 0;
        }

        if (!!obj && typeof obj.length === 'number') {
          return obj.length === 0;
        }

        return false;
      },

      /**
       * Test if this object has this key.
       *
       * @method hasKey
       * @static
       * @param {Object} obj
       * @param {Object} key
       * @return {Boolean}
       */
      hasKey: function(obj, key) {
        return !!obj && Object.prototype.hasOwnProperty.call(obj, key);
      },

      //since R doesn't handle object :(
      /**
       * Perform reduce on this object.
       *
       * @method reduceObj
       * @static
       * @param {Function} f
       * @param {Object} memo
       * @param {Object} obj
       * @return {Object}  memo
       */
      reduceObj: function(f, memo, obj) {
        return R.reduce(function(sum, pair) {
          return f(sum, pair[1], pair[0]);
        },
        memo,
        R.toPairs(obj));
      },

      /**
       * Iterate over this object [k,v] pairs and call f(v,k).
       *
       * @method eachObj
       * @static
       * @param {Function} f
       * @param {Object} obj
       * @return {Object} original object
       */
      eachObj: function(f, obj) {
        R.forEach(function(pair) {
          return f(pair[1], pair[0]);
        },
        R.toPairs(obj));
        return obj;
      },

      /**
       * Mixin this object.
       *
       * @method mixes
       * @param {Object} object
       * @return {Claxx}
       */
      mixes: function(obj) {
        return klass.mixes(obj);
      },

      /**
       * @property {Logger} logger Short cut to logger
       * @static
       */
      logger: DBG,

      /**
       * @property {Logger} loggr Short cut to logger
       * @static
       */
      loggr: DBG,

      /**
       * @property {Ramda} ramda Short cut to Ramda
       * @static
       */
      ramda: R,

      /**
       * @property {Ramda} R Short cut to Ramda
       * @static
       */
      R: R

    };

    return exports;
});

//////////////////////////////////////////////////////////////////////////////
//EOF

