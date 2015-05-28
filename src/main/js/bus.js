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
    let exports={},
    R = sjs.ramda,
    undef,
    _SEED=0;

    //////////////////////////////////////////////////////////////////////////////
    const mkSubSCR = (topic, selector, target, repeat, args) => {
      return {
        id: "sub#" + Number(++_SEED),
        repeat: sjs.boolify(repeat),
        args: args || [],
        action: selector,
        target: target,
        topic: topic,
        active: true
      };
    }

    //////////////////////////////////////////////////////////////////////////////
    const mkTreeNode = () => {
      return {
        tree: {},  // children - branches
        subs: []   // subscribers (callbacks)
      };
    }

    //////////////////////////////////////////////////////////////////////////////
    /**
     * @class EventBus
     */
    class EventBus {
      /**
       * Subscribe to 1+ topics, returning a list of subscriber handles.
       * topics => "/hello/*  /goodbye/*"
       * @memberof module:cherimoia/ebus~EventBus
       * @method once
       * @param {String} topics - space separated if more than one.
       * @param {Function} selector
       * @param {Object} target
       * @return {Array.String} - subscription ids
       */
      once(topics, selector, target /*, more args */) {
        const rc= this.pkListen(false,
                                topics,
                                selector,
                                target,
                                sjs.dropArgs(arguments,3));
        return sjs.echt(rc) ? rc : [];
      }

      /**
       * subscribe to 1+ topics, returning a list of subscriber handles.
       * topics => "/hello/*  /goodbye/*"
       * @memberof module:cherimoia/ebus~EventBus
       * @method on
       * @param {String} topics - space separated if more than one.
       * @param {Function} selector
       * @param {Object} target
       * @return {Array.String} - subscription ids.
       */
      on(topics, selector, target /*, more args */) {
        const rc= this.pkListen(true,
                                topics,
                                selector,
                                target,
                                sjs.dropArgs(arguments,3));
        return sjs.echt(rc) ? rc : [];
      }

      /**
       * Trigger event on this topic.
       * @memberof module:cherimoia/ebus~EventBus
       * @method fire
       * @param {String} topic
       * @param {Object} msg
       * @return {Boolean}
       */
      fire(topic, msg) {
        const tokens= sjs.safeSplit(topic,'/');
        if (tokens.length > 0 ) {
          return this.pkDoPub(topic, this.rootNode, tokens, 0, msg || {} );
        } else {
          return false;
        }
      }

      /**
       * Resume actions on this handle.
       * @memberof module:cherimoia/ebus~EventBus
       * @method resume
       * @param {Object} - handler id
       */
      resume(handle) {
        const sub= this.allSubs[handle];
        if (!!sub) {
          sub.active=true;
        }
      }

      /**
       * Pause actions on this handle.
       * @memberof module:cherimoia/ebus~EventBus
       * @method pause
       * @param {Object} - handler id
       */
      pause(handle) {
        const sub= this.allSubs[handle];
        if (!!sub) {
          sub.active=false;
        }
      }

      /**
       * Stop actions on this handle.
       * Unsubscribe.
       * @memberof module:cherimoia/ebus~EventBus
       * @method off
       * @param {Object} - handler id
       */
      off(handle) {
        const sub= this.allSubs[handle];
        if (!!sub) {
          this.pkUnSub(this.rootNode, sjs.safeSplit(sub.topic,'/'), 0, sub);
        }
      }

      /**
       * Remove all subscribers.
       * @memberof module:cherimoia/ebus~EventBus
       * @method removeAll
       */
      removeAll() {
        this.rootNode = mkTreeNode();
        this.allSubs = {};
      }

      /**
       * @private
       */
      pkGetSubcr(id) {
        return this.allSubs[id];
      }

      /**
       * @private
       */
      pkListen(repeat, topics, selector, target, more) {
        const ts= topics.trim().split(/\s+/);
        // for each topic, subscribe to it.
        const rc= R.map((t) => {
          return this.pkAddSub(repeat,t,selector,target,more);
        }, ts);
        return R.reject((z)=> { return z.length===0; }, rc);
      }

      /**
       * Register a subscriber to the topic leaf node, creating the path
       * when necessary.
       * @private
       */
      pkAddSub(repeat, topic, selector, target, more) {
        const tkns= sjs.safeSplit(topic,'/');
        if (tkns.length > 0) {
          const rc= mkSubSCR(topic, selector, target, repeat, more),
          node= R.reduce((memo, z) => {
            return this.pkDoSub(memo,z);
          },
          this.rootNode,
          tkns);

          this.allSubs[rc.id] = rc;
          node.subs.push(rc);
          return rc.id;
        } else {
          return '';
        }
      }

      /**
       * Remove the subscriber and trim if it is a dangling leaf node.
       * @private
       */
      pkUnSub(node, tokens, pos, sub) {
        if (! sjs.echt(node)) { return; }
        if (pos < tokens.length) {
          const k= tokens[pos],
          cn= node.tree[k];
          this.pkUnSub(cn, tokens, pos+1, sub);
          if (R.keys(cn.tree).length === 0 &&
              cn.subs.length === 0) {
            delete node.tree[k];
          }
        } else {
          pos= -1;
          R.find((z) => {
            pos += 1;
            if (z.id === sub.id) {
              delete this.allSubs[z.id];
              node.subs.splice(pos,1);
              return true;
            } else {
              return false;
            }
          }, node.subs);
        }
      }

      /**
       * @private
       */
      pkDoPub(topic, node, tokens, pos, msg) {
        if (! sjs.echt(node)) { return false; }
        let rc=false;
        if (pos < tokens.length) {
          rc = rc || this.pkDoPub(topic, node.tree[ tokens[pos] ], tokens, pos+1, msg);
          rc = rc || this.pkDoPub(topic, node.tree['*'], tokens, pos+1,msg);
        } else {
          rc = rc || this.pkRun(topic, node,msg);
        }
        return rc;
      }

      /**
       * Invoke subscribers, and for non repeating ones, remove them from
       * the list.
       * @private
       */
      pkRun(topic, node, msg) {
        const cs= !!node ? node.subs : [];
        let purge=false,
        rc=false;

        R.forEach((z) => {
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
        }, cs);

        // get rid of unwanted ones, and reassign new set to the node.
        if (purge && cs.length > 0) {
          node.subs= R.filter((z) => {
            return z.action ? true : false;
          }, cs);
        }

        return rc;
      }

      /**
       * Find or create a new child node.
       * @private
       */
      pkDoSub(node,token) {
        if (! sjs.hasKey(node.tree, token)) {
          node.tree[token] = mkTreeNode();
        }
        return node.tree[token];
      }

      /**
       * @private
       */
      constructor() {
        this.rootNode = mkTreeNode();
        this.allSubs = {};
      }

    };

    /**
     * @method reify
     * @return {EventBus}
     */
    exports.reify= () => { return new EventBus(); },

    /**
     * @property {EventBus} EventBus
     */
    exports.EventBus= EventBus;

    return exports;
});

//////////////////////////////////////////////////////////////////////////////
//EOF

