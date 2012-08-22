function quoteReply (elem) {
	$(elem).addClass('active');
	setTimeout(function(){
		$('.active').removeClass('active');
	},200);
	while (!elem.id){
		elem = elem.parentNode;
	}
	if (elem != undefined) {
		Android.citePosting(elem.id);
	}
}

function quoteClick (elem) {
	var offset = $(elem).offset();

	if (!($('#quoteoverlay').is(':visible'))) {
		$('#quoteoverlay')
		.css("display", "block")
		.css("top", offset.top);
	} 
	offset.top = getCloneTop ();
	var href = $(elem).attr('href');
	var segments = href.split("/");
	var id = segments[segments.length - 1];
	var div = cloneNode (id, offset.top);
	Android.debugString ($(div).html())
	var cButton = $(div).prepend('<p class="closebutton">close</p>');
	$('#quoteoverlay').click (function() {
		Android.debugString ($(this).html())
		$('#backdrop').fadeOut();
		$('#quoteoverlay').css("display", "none")
		$('body').css("overflow", "visible");
		$(div).remove();
	});
	var cloneBottom = offset.top+$(div).outerHeight(true);

	var parentId = $(elem).parents("div").attr('id');
	$('#backdrop').fadeIn();
	$('html,body').animate({scrollTop: $("#"+parentId).offset().top},'slow', function() {
		//$('html,body').css("overflow", "hidden");
	});	
	$('#backdrop').css("top", $(elem).parents("div").offset().top);
	return false; 
}

function cloneNode (id, topOffset) {
	var div = $('div#' + id).clone(true, true)
	.attr('id', getCloneID (id))
	.attr('class','cloned')
	.css("top", topOffset + 20)
	.appendTo('#quoteoverlay');
	return div;
}

function getCloneID (id) {
	return id + '_cloned';
}

function getCloneTop () {
	var top = 0;
	var lastClone = ($('#quoteoverlay div.cloned').last());
	if (null != lastClone.offset()) {
		top = $(lastClone).position().top + 60;
	} else {
		top = 20;
	}
	return top;
}

function goToByScroll(id){     			
	$('html,body').animate({scrollTop: $("#"+id).offset().top},'slow');		
}

function showCollapsed (collapse) {
	if (collapse) {
		$(".read").hide();
	} else {
		$(".read").show();
	} 
}

function getRule (ruleName) {
	var mysheet=document.styleSheets[0];
	var myrules=mysheet.cssRules? mysheet.cssRules: mysheet.rules;
	for (i=0; i<myrules.length; i++) {
		if(myrules[i].selectorText.toLowerCase()==ruleName) { 
			return myrules[i];
		}
	}
	return undefined;
}

function markAllPostingsRead () {
	$('ul#kc-postlist>li').removeClass('read').removeClass('unread').addClass('read');
	$('ul#kc-postlist>li#first').removeClass('read');
	$('ul#kc-postlist>li.read').hide();
}

function appendPost (content, className, id) {
	if (content != undefined) {
		var d = document.createElement("li");
		d.className = d.className + " " + className;
		d.innerHTML = content;
		var ul = document.getElementById("kc-postlist");
		ul.appendChild (d);
		if (id != undefined) {
			d.id = id;
		}
		$('time.timeago', d).timeago();
		$('.posthead', d).click(function(){
			quoteReply(this);
			
		});
		/*console.log (ul.innerHTML);  for some reasons I don't understand,
			removing this log-command leads to the error:
			08-04 00:39:42.883: E/Web Console(16668): Uncaught ReferenceError: appendPost is not defined at null:1*/
	} 
}

function postingsDone() {
	console.log (document.documentElement.innerHTML);
}