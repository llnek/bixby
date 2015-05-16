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
 * @module cherimoia/ebus
 */
define("cherimoia/ebus",

       ['cherimoia/skarojs'],

  function (sjs) { "use strict";

    /** @alias module:cherimoia/ebus */
    var exports={},
    R = sjs.ramda,
    undef,
    _SEED=0;

    //////////////////////////////////////////////////////////////////////////////
    //
    function mkSubSCR(topic, selector, target, repeat, args) {
      return {
        id: "sub-" + Number(++_SEED),
        repeat: sjs.boolify(repeat),
        args: args || [],
        action: selector,
        target: target,
        topic: topic,
        active: true
      };
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    function mkTreeNode() {
      return {
        tree: {},  // children - branches
        subs: []   // subscribers (callbacks)
      };
    }

    //////////////////////////////////////////////////////////////////////////////
    //
    /**
     * @class EventBus
     */
    var EventBus = sjs.mixes({

      /**
       * Subscribe to 1+ topics, returning a list of subscriber handles.
       * topics => "/hello/*  /goodbye/*"
       *
       * @memberof module:cherimoia/ebus~EventBus
       * @method once
       * @param {String} topics - space separated if more than one.
       * @param {Function} selector
       * @param {Object} target
       * @return {Array.String} - subscription ids
       */
      once: function(topics, selector, target /*, more args */) {
        var rc= this.pkListen(false,
                              topics,
                              selector,
                              target,
                              sjs.dropArgs(arguments,3));
        return sjs.echt(rc) ? rc : [];
      },

      /**
       * subscribe to 1+ topics, returning a list of subscriber handles.
       * topics => "/hello/*  /goodbye/*"
       *
       * @memberof module:cherimoia/ebus~EventBus
       * @method on
       * @param {String} topics - space separated if more than one.
       * @param {Function} selector
       * @param {Object} target
       * @return {Array.String} - subscription ids.
       */
      on: function(topics, selector, target /*, more args */) {
        var rc= this.pkListen(true,
                              topics,
                              selector,
                              target,
                              sjs.dropArgs(arguments,3));
        return sjs.echt(rc) ? rc : [];
      },

      /**
       * Trigger event on this topic.
       *
       * @memberof module:cherimoia/ebus~EventBus
       * @method fire
       * @param {String} topic
       * @param {Object} msg
       * @return {Boolean}
       */
      fire: function(topic, msg) {
        var tokens= sjs.safeSplit(topic,'/');
        if (tokens.length > 0 ) {
          return this.pkDoPub(topic, this.rootNode, tokens, 0, msg || {} );
        }
        return false;
      },

      /**
       * Resume actions on this handle.
       *
       * @memberof module:cherimoia/ebus~EventBus
       * @method resume
       * @param {Object} - handler id
       */
      resume: function(handle) {
        var sub= this.allSubs[handle];
        if (sub) {
          sub.active=true;
        }
      },

      /**
       * Pause actions on this handle.
       *
       * @memberof module:cherimoia/ebus~EventBus
       * @method pause
       * @param {Object} - handler id
       */
      pause: function(handle) {
        var sub= this.allSubs[handle];
        if (sub) {
          sub.active=false;
        }
      },


      /**
       * Stop actions on this handle.
       * Unsubscribe.
       *
       * @memberof module:cherimoia/ebus~EventBus
       * @method off
       * @param {Object} - handler id
       */
      off: function(handle) {
        var sub= this.allSubs[handle];
        if (sub) {
          this.pkUnSub(this.rootNode, sjs.safeSplit(sub.topic,'/'), 0, sub);
        }
      },

      /**
       * Remove all subscribers.
       *
       * @memberof module:cherimoia/ebus~EventBus
       * @method removeAll
       */
      removeAll: function() {
        this.rootNode = mkTreeNode();
        this.allSubs = {};
      },

      /**
       * @private
       */
      pkGetSubcr: function(id) {
        return this.allSubs[id];
      },

      /**
       * @private
       */
      pkListen: function(repeat, topics, selector, target, more) {
        var ts= topics.trim().split(/\s+/);
        // for each topic, subscribe to it.
        var rc= R.map(function(t) {
          return this.pkAddSub(repeat,t,selector,target,more);
        }.bind(this), ts);
        return R.reject(function(z) { return z.length===0; }, rc);
      },

      /**
       * Register a subscriber to the topic leaf node, creating the path
       * when necessary.
       *
       * @private
       */
      pkAddSub: function(repeat, topic, selector, target, more) {
        var tkns= sjs.safeSplit(topic,'/');
        if (tkns.length > 0) {
          var rc= mkSubSCR(topic, selector, target, repeat, more),
          node= R.reduce(function(memo, z) {
            return this.pkDoSub(memo,z);
          }.bind(this), this.rootNode, tkns);
          this.allSubs[rc.id] = rc;
          node.subs.push(rc);
          return rc.id;
        } else {
          return '';
        }
      },

      /**
       * Remove the subscriber and trim if it is a dangling leaf node.
       *
       * @private
       */
      pkUnSub: function(node, tokens, pos, sub) {
        if (! sjs.echt(node)) { return; }
        if (pos < tokens.length) {
          var k= tokens[pos],
          cn= node.tree[k];
          this.pkUnSub(cn, tokens, pos+1, sub);
          if (R.keys(cn.tree).length === 0 &&
              cn.subs.length === 0) {
            delete node.tree[k];
          }
        } else {
          pos= -1;
          R.find(function(z) {
            pos += 1;
            if (z.id === sub.id) {
              delete this.allSubs[z.id];
              node.subs.splice(pos,1);
              return true;
            }
            return false;
          }.bind(this), node.subs);
        }
      },

      /**
       * @private
       */
      pkDoPub: function(topic, node, tokens, pos, msg) {
        if (! sjs.echt(node)) { return false; }
        var rc=false;
        if (pos < tokens.length) {
          rc = rc || this.pkDoPub(topic, node.tree[ tokens[pos] ], tokens, pos+1, msg);
          rc = rc || this.pkDoPub(topic, node.tree['*'], tokens, pos+1,msg);
        } else {
          rc = rc || this.pkRun(topic, node,msg);
        }
        return rc;
      },

      /**
       * Invoke subscribers, and for non repeating ones, remove them from
       * the list.
       *
       * @private
       */
      pkRun: function(topic, node, msg) {
        var cs= !!node ? node.subs : [],
        rc=false,
        purge=false;
        R.forEach(function (z) {
          if (z.active &&
              sjs.echt(z.action)) {
            // pass along any extra parameters, if any.
            z.action.apply(z.target, [topic, msg].concat(z.args));
            // if once only, kill it.
            if (!z.repeat) {
              delete this.allSubs[z.id];
              z.active= false;
              z.action= null;
              purge=true;
            }
            rc = true;
          }
        }.bind(this), cs);
        // get rid of unwanted ones, and reassign new set to the node.
        if (purge && cs.length > 0) {
          node.subs= R.filter(function(z) {
            if (z.action) { return true; } else { return false; }
          },cs);
        }
        return rc;
      },

      /**
       * Find or create a new child node.
       *
       * @private
       */
      pkDoSub: function(node,token) {
        if ( ! sjs.hasKey(node.tree, token)) {
          node.tree[token] = mkTreeNode();
        }
        return node.tree[token];
      },

      /**
       * @private
       */
      ctor: function() {
        this.rootNode = mkTreeNode();
        this.allSubs = {};
      }

    });

    /**
     * @method reify
     * @static
     * @return {EventBus}
     */
    exports.reify= function() { return new EventBus(); },

    /**
     * @property {EventBus} EventBus
     * @static
     * @final
     */
    exports.EventBus= EventBus;

    return exports;
});

//////////////////////////////////////////////////////////////////////////////
//EOF

