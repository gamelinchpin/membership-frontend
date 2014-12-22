require([
    'src/utils/analytics/setup',
    'src/utils/cookieRefresh',
    'ajax',
    'src/modules/tier/JoinFree',
    'src/modules/info/Feedback',
    'src/modules/tier/PaidForm',
    'src/modules/tier/StaffForm',
    'src/modules/events/Cta',
    'src/modules/events/filter',
    'src/modules/events/toggle',
    'src/modules/slideshow',
    'src/modules/images',
    'src/modules/sticky',
    'src/modules/Header',
    'src/modules/UserDetails',
    'src/modules/tier/choose',
    'src/modules/events/eventPriceEnhance',
    'src/modules/tier/Thankyou',
    'src/modules/patterns',
    'src/utils/addToClipboard',
    'src/utils/modal',
    'src/utils/form/processSubmit',
    'lib/bower-components/raven-js/dist/raven', // add new deps ABOVE this
    'src/utils/modernizr'
], function(
    analytics,
    cookieRefresh,
    ajax,
    JoinFree,
    FeedbackForm,
    PaidForm,
    StaffForm,
    Cta,
    Filter,
    toggle,
    slideshow,
    images,
    sticky,
    Header,
    UserDetails,
    choose,
    eventPriceEnhance,
    Thankyou,
    patterns,
    addToClipboard,
    modal,
    processSubmit
) {
    'use strict';

    /*global Raven */
    // set up Raven, which speaks to Sentry to track errors
    Raven.config('https://e159339ea7504924ac248ba52242db96@app.getsentry.com/29912', {
        whitelistUrls: ['membership.theguardian.com/assets/'],
        tags: { build_number: guardian.membership.buildNumber }
    }).install();

    ajax.init({page: {ajaxUrl: ''}});

    // TODO: Remove this, see module
    cookieRefresh.init();

    analytics.init();

    // Global
    toggle.init();
    images.init();
    slideshow.init();
    sticky.init();
    var header = new Header();
    header.init();
    (new UserDetails()).init();

    // Events
    (new Cta()).init();
    eventPriceEnhance.init();

    // Join
    choose.init();
    (new JoinFree()).init();
    (new PaidForm()).init();
    (new StaffForm()).init();
    (new Thankyou()).init(header);
    processSubmit.init();

    // Feedback
    (new FeedbackForm()).init();

    // Pattern library
    patterns.init();

    // Test Users
    addToClipboard.init();

    // Modal
    modal.init();
});
