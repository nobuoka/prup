defineInputParams {
    define 'package_name'
    define 'ababa'
}
defineParamsUpdater { params ->
    params['package_path'] = params['package_name'].replaceAll(/\./, '/')
}
defineGeneratingRule {
    define 'src/yourpackagename/Test.java', 'src/${package_path}/Test.java'
}
