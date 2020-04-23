package ee.taltech.spormapsapp.db

class LocationAlias {
    var hash: String = "?"
    var alias: String = "?"
    var locations: Int = 0
    var firstDate: String = "0"

    constructor(hash: String, alias: String, locations: Int, firstDate: String) {
        this.hash = hash
        this.alias = alias
        this.locations = locations
        this.firstDate = firstDate
    }
}