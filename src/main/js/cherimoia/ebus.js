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
 * @module cherimoia/ebus
 */
import sjs from "cherimoia/skaro";

let R = sjs.ramda,
undef,
_SEED=0;

//////////////////////////////////////////////////////////////////////////////
const mkSubSCR = (topic, selector, context, repeat, args) => {
  return {
    target:  !!context ? context : null,
    id: "sub#" + Number(++_SEED),
    repeat: sjs.boolify(repeat),
    args: args || [],
    action: selector,
    topic: topic,
    active: true
  };
}

//////////////////////////////////////////////////////////////////////////////
const mkTreeNode = (root) => {
  return root ? { tree: {} } : {
    tree: {},  // children - branches
    subs: []   // subscribers
  };
}

//////////////////////////////////////////////////////////////////////////////
const _listen = function(repeat, topics, selector, target, more) {
  const ts= topics.trim().split(/\s+/),
  // for each topic, subscribe to it.
  rc= R.map(t => {
    return _addSub.call(this, repeat,t,selector,target,more);
  }, ts);
  return R.reject( z => { return z.length===0; }, rc);
}

//////////////////////////////////////////////////////////////////////////////
const _doSub = (node,token) => {
  if (! sjs.hasKey(node.tree, token)) {
    node.tree[token] = mkTreeNode();
  }
  return node.tree[token];
}

//////////////////////////////////////////////////////////////////////////////
const _addSub = function(repeat, topic, selector, target, more) {
  let tkns= this.splitTopic(topic),
  rcid='';
  if (tkns.length > 0) {
    const rc= mkSubSCR(topic, selector, target, repeat, more),
    node= R.reduce((memo, z) => {
      return _doSub(memo,z);
    },
    this.root, tkns);

    this.subs[rc.id] = rc;
    node.subs.push(rc);
    rcid= rc.id;
  }
  return rcid;
}

//////////////////////////////////////////////////////////////////////////////
const _unSub = function(node, tokens, pos, sub) {
  if (! sjs.echt(node)) { return; }
  if (pos < tokens.length) {
    const k= tokens[pos],
    cn= node.tree[k];
    _unSub.call(this, cn, tokens, pos+1, sub);
    if (R.keys(cn.tree).length === 0 &&
        cn.subs.length === 0) {
      delete node.tree[k];
    }
  } else {
    pos = -1;
    R.find( z => {
      pos += 1;
      if (z.id === sub.id) {
        delete this.subs[z.id];
        node.subs.splice(pos,1);
        return true;
      }
    }, node.subs);
  }
}

//////////////////////////////////////////////////////////////////////////////
const _doPub = function(topic, node, tokens, pos, msg) {
  if (! sjs.echt(node)) { return false; }
  let rc=false;
  if (pos < tokens.length) {
    rc = rc || _doPub.call(this,topic, node.tree[ tokens[pos] ], tokens, pos+1, msg);
    rc = rc || _doPub.call(this,topic, node.tree['*'], tokens, pos+1,msg);
  } else {
    rc = rc || _run.call(this,topic, node,msg);
  }
  return rc;
}

//////////////////////////////////////////////////////////////////////////////
const _run = function(topic, node, msg) {
  const cs= !!node ? node.subs : [];
  let purge=false,
  rc=false;

  R.forEach( z => {
    if (z.active &&
        sjs.echt(z.action)) {
      // pass along any extra parameters, if any.
      z.action.apply(z.target, [msg,topic].concat(z.args));
      // if once only, kill it.
      if (!z.repeat) {
        delete this.subs[z.id];
        z.active= false;
        z.action= null;
        purge=true;
      }
      rc = true;
    }
  }, cs);

  // get rid of unwanted ones, and reassign new set to the node.
  if (purge && cs.length > 0) {
    node.subs= R.filter( z => {
      return z.action ? true : false;
    }, cs);
  }

  return rc;
}

//////////////////////////////////////////////////////////////////////////////
/** @class RvBus */
class RvBus extends sjs.ES6Claxx {
  /**
   * Subscribe to 1+ topics, returning a list of subscriber handles.
   * topics => "/hello/*  /goodbye/*"
   * @memberof module:cherimoia/ebus~RvBus
   * @method once
   * @param {String} topics - space separated if more than one.
   * @param {Function} selector
   * @param {Object} target
   * @return {Array.String} - subscription ids
   */
  once(topics, selector, target /*, more args */) {
    const rc= _listen.call(this,false, topics,
                            selector, target,
                            sjs.dropArgs(arguments,3));
    return sjs.echt(rc) ? rc : [];
  }

  /**
   * subscribe to 1+ topics, returning a list of subscriber handles.
   * topics => "/hello/*  /goodbye/*"
   * @memberof module:cherimoia/ebus~RvBus
   * @method on
   * @param {String} topics - space separated if more than one.
   * @param {Function} selector
   * @param {Object} target
   * @return {Array.String} - subscription ids.
   */
  on(topics, selector, target /*, more args */) {
    const rc= _listen.call(this,true, topics,
                            selector, target,
                            sjs.dropArgs(arguments,3));
    return sjs.echt(rc) ? rc : [];
  }

  /**
   * Trigger event on this topic.
   * @memberof module:cherimoia/ebus~RvBus
   * @method fire
   * @param {String} topic
   * @param {Object} msg
   * @return {Boolean}
   */
  fire(topic, msg) {
    let tokens= this.splitTopic(topic),
    rc=false;
    if (tokens.length > 0 ) {
      rc= _doPub.call(this,topic,
                       this.root, tokens, 0, msg || {} );
    }
    return rc;
  }

  /**
   * @method splitTopic
   * @protected
   */
  splitTopic(topic) {
    return sjs.safeSplit(topic,'/');
  }

  /**
   * Resume actions on this handle.
   * @memberof module:cherimoia/ebus~RvBus
   * @method resume
   * @param {Object} - handler id
   */
  resume(handle) {
    const sub= this.subs[handle];
    if (!!sub) {
      sub.active=true;
    }
  }

  /**
   * Pause actions on this handle.
   * @memberof module:cherimoia/ebus~RvBus
   * @method pause
   * @param {Object} - handler id
   */
  pause(handle) {
    const sub= this.subs[handle];
    if (!!sub) {
      sub.active=false;
    }
  }

  /**
   * Stop actions on this handle.
   * Unsubscribe.
   * @memberof module:cherimoia/ebus~RvBus
   * @method off
   * @param {Object} - handler id
   */
  off(handle) {
    const sub= this.subs[handle];
    if (!!sub) {
      _unSub.call(this,this.root,
                   this.splitTopic(sub.topic), 0, sub);
    }
  }

  /**
   * @method iniz
   * @private
   */
  iniz() {
    this.root= mkTreeNode(true);
    this.subs = {};
  }

  /**
   * Remove all subscribers.
   * @memberof module:cherimoia/ebus~RvBus
   * @method removeAll
   */
  removeAll() {
    this.iniz();
  }

  /**
   * @method constructor
   * @private
   */
  constructor() {
    super();
    this.iniz();
  }

};
//////////////////////////////////////////////////////////////////////////////
/**
 * @class EventBus
 */
class EventBus extends RvBus {
  /**
   * Trigger event on this topic.
   * @memberof module:cherimoia/ebus~EventBus
   * @method fire
   * @param {String} topic
   * @param {Object} msg
   * @return {Boolean}
   */
  fire(topic, msg) {
    return _run.call(this, topic,
                     this.root.tree[topic], msg || {});
  }

  /**
   * @method splitTopic
   * @protected
   */
  splitTopic(topic) { return [topic]; }

  /**
   * @method constructor
   * @private
   */
  constructor() {
    super();
  }
}

/** @alias module:cherimoia/ebus */
const xbox= /** @lends xbox# */{
  /**
   * @method reifyRvBus
   * @return {RvBus}
   */
  reifyRvBus() {
    return new RvBus();
  },
  /**
   * @method reify
   * @return {EventBus}
   */
  reify() {
    return new EventBus();
  },
  /**
   * @property {EventBus} EventBus
   */
  EventBus: EventBus,
  /**
   * @property {RvBus} RvBus
   */
  RvBus: RvBus
};

sjs.merge(exports, xbox);
/*@@
return xbox;
@@*/

//////////////////////////////////////////////////////////////////////////////
//EOF

