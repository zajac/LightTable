if(!lt.util.load.provided_QMARK_('lt.plugins.user')) {
goog.provide('lt.plugins.user');
goog.require('cljs.core');
goog.require('lt.util.load');
goog.require('lt.util.load');
goog.require('lt.objs.command');
goog.require('lt.objs.command');
goog.require('lt.objs.tabs');
goog.require('lt.objs.tabs');
goog.require('lt.object');
goog.require('lt.object');
lt.plugins.user.hello_panel = (function hello_panel(this$){var e__7762__auto__ = crate.core.html.call(null,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"h1","h1",1013907515),"Hello World!"], null));var seq__7874_7880 = cljs.core.seq.call(null,cljs.core.partition.call(null,2,cljs.core.PersistentVector.EMPTY));var chunk__7875_7881 = null;var count__7876_7882 = 0;var i__7877_7883 = 0;while(true){
if((i__7877_7883 < count__7876_7882))
{var vec__7878_7884 = cljs.core._nth.call(null,chunk__7875_7881,i__7877_7883);var ev__7763__auto___7885 = cljs.core.nth.call(null,vec__7878_7884,0,null);var func__7764__auto___7886 = cljs.core.nth.call(null,vec__7878_7884,1,null);lt.util.dom.on.call(null,e__7762__auto__,ev__7763__auto___7885,func__7764__auto___7886);
{
var G__7887 = seq__7874_7880;
var G__7888 = chunk__7875_7881;
var G__7889 = count__7876_7882;
var G__7890 = (i__7877_7883 + 1);
seq__7874_7880 = G__7887;
chunk__7875_7881 = G__7888;
count__7876_7882 = G__7889;
i__7877_7883 = G__7890;
continue;
}
} else
{var temp__4126__auto___7891 = cljs.core.seq.call(null,seq__7874_7880);if(temp__4126__auto___7891)
{var seq__7874_7892__$1 = temp__4126__auto___7891;if(cljs.core.chunked_seq_QMARK_.call(null,seq__7874_7892__$1))
{var c__7119__auto___7893 = cljs.core.chunk_first.call(null,seq__7874_7892__$1);{
var G__7894 = cljs.core.chunk_rest.call(null,seq__7874_7892__$1);
var G__7895 = c__7119__auto___7893;
var G__7896 = cljs.core.count.call(null,c__7119__auto___7893);
var G__7897 = 0;
seq__7874_7880 = G__7894;
chunk__7875_7881 = G__7895;
count__7876_7882 = G__7896;
i__7877_7883 = G__7897;
continue;
}
} else
{var vec__7879_7898 = cljs.core.first.call(null,seq__7874_7892__$1);var ev__7763__auto___7899 = cljs.core.nth.call(null,vec__7879_7898,0,null);var func__7764__auto___7900 = cljs.core.nth.call(null,vec__7879_7898,1,null);lt.util.dom.on.call(null,e__7762__auto__,ev__7763__auto___7899,func__7764__auto___7900);
{
var G__7901 = cljs.core.next.call(null,seq__7874_7892__$1);
var G__7902 = null;
var G__7903 = 0;
var G__7904 = 0;
seq__7874_7880 = G__7901;
chunk__7875_7881 = G__7902;
count__7876_7882 = G__7903;
i__7877_7883 = G__7904;
continue;
}
}
} else
{}
}
break;
}
return e__7762__auto__;
});
lt.object.object_STAR_.call(null,new cljs.core.Keyword("lt.plugins.user","user.hello","lt.plugins.user/user.hello",3780889681),new cljs.core.Keyword(null,"tags","tags",1017456523),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"user.hello","user.hello",1535287393)], null),new cljs.core.Keyword(null,"behaviors","behaviors",607554515),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword("lt.plugins.user","on-close-destroy","lt.plugins.user/on-close-destroy",4509098889)], null),new cljs.core.Keyword(null,"init","init",1017141378),(function (this$){return lt.plugins.user.hello_panel.call(null,this$);
}));
lt.plugins.user.__BEH__on_close_destroy = (function __BEH__on_close_destroy(this$){return lt.object.raise.call(null,this$,new cljs.core.Keyword(null,"destroy","destroy",2571277164));
});
lt.object.behavior_STAR_.call(null,new cljs.core.Keyword("lt.plugins.user","on-close-destroy","lt.plugins.user/on-close-destroy",4509098889),new cljs.core.Keyword(null,"triggers","triggers",2516997421),new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"close","close",1108660586),null], null), null),new cljs.core.Keyword(null,"reaction","reaction",4441361819),lt.plugins.user.__BEH__on_close_destroy);
lt.plugins.user.hello = lt.object.create.call(null,new cljs.core.Keyword("lt.plugins.user","user.hello","lt.plugins.user/user.hello",3780889681));
lt.objs.command.command.call(null,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"command","command",1964298941),new cljs.core.Keyword(null,"user.say-hello","user.say-hello",576535935),new cljs.core.Keyword(null,"desc","desc",1016984067),"User: Say Hello",new cljs.core.Keyword(null,"exec","exec",1017031683),(function (){return lt.objs.tabs.add_or_focus_BANG_.call(null,lt.plugins.user.hello);
})], null));
}

//# sourceMappingURL=user_compiled.js.map