(function () {
    // Add catalog configuration entry
    if ($('.cascade-menu-system').find('.divider').length === 0) {
        $('.cascade-menu-system').append('<li role="separator" class="divider"></li>');
    }
    if ($('.cascade-menu-system').find('.menu-prov').length === 0) {
        $('.cascade-menu-system').append('<li class="menu-prov"><a href="#/prov/catalog">Configure plugin-prov</a></li>');
    }
})();
