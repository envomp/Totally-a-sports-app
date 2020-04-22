package ee.taltech.spormapsapp.db

class LocationCategoryParser {
    var hash: String = "?"
    var alias: String = "?"
    var locations: Int = 0
    var firstDate: Int = 0

    constructor(hash: String, alias: String, locations: Int, firstDate: Int) {
        this.hash = hash
        this.alias = alias
        this.locations = locations
        this.firstDate = firstDate
    }
}