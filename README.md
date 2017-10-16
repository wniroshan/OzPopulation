# Constructing The Population

This code was developed for Life Time Affordable and Tennable City Housing (LATCH) project that was funded in part by the Australian Research Council, SJB Urban and the (Melbourne) Metropolitan Planning Authority through Linkage Project grant LP130100008.

The purpose of the tool is generating the population (households with persons) for the specified statistical areas using census data tables from Australian Bureau of Statistics, and then assiging the constructed households to addresses in given in geographical data files obtained from Vicmap data (GIS - Shapefiles). The households are assigned according to the distributions observed in census data.

Already downloaded data made available with this tool covers 613 SA1 areas that fall within Darebin (North, South) and Banyule. Population statistics were downloaded using TableBuilder Pro tool provided in Australian Bureau of Statics (ABS) website(www.abs.gov.au). ESRI shapefiles of SA1 and SA2 area boundaries were also downloaded from ABS website. ESRI shapefiles of building addresses were taken from Vicmap Data provided in www.land.vic.gov.au. However, we have not included the shapefile with SA1 areas due to space limitations. Refer to below No. 3 to learn how to download shapefiles.

## Prerequisits
* R statistical package (https://www.r-project.org). Required R extra libraries are:
     * stringr
     * optparse
     * Metrics
* JAVA 8 (https://java.com/en/download/)
* Maven (https://maven.apache.org/download.cgi)
* ABS TableBuilder Pro to download census data

## Constructing a new population
### 1. Download data using ABS TableBuilder tool

ABS TableBuilder tutorial is available at this [link](http://www.abs.gov.au/websitedbs/censushome.nsf/home/tablebuildertutorials). You need to know how to create and manage tables, change databases, create custom data fields and download data tables in csv format.

   * Number of persons by Relationship Status (RLHP) by Sex (SEXP) by Age group (AGE5P) by SA2. 

     Construct the below table using "Counting Persons, Place of Usual Residence" database. Codes used for table headers are used in TableBuilder. Remove `Total` fields from the table by clicking on "Hide Total" button if it is already selected, and save the table as `buildpopulation/data/latch/raw/SA2, RLHP Relationship in Household, SEXP and AGE5P.csv`.
```
|---------------------------| Persons, Place of usual residence |
| SA2 | RLHP | SEXP | AGE5P |                                   |
|-----|------|------|-------|-----------------------------------|
|     |      |      |       |                 x                 |
|     |      |      |       |                 x                 |
```
  We are using custom data categories for Relationship status and Age groups. Custom categories can be formed by combining existing categories in TableBuilder's "My Custom Data" section. Refer to `buildpopulation/doc/latch/Custom Individual relationship categories.pdf` for custom categories
  
  Age groups used here are 0-14,15-24,25-34,35-49,50-64, ... ,85-99,100++. 
      
  * Number of households by Number of persons in household (NPRD) by Family Household Composition (Dwelling) (HCFMD). 

    Use Construct below table using "Counting Families, Place of Usual Residence" database and save as `buildpopulation/data/latch/raw/SA2, NPRD and HCFMD.csv`. This table should not have any `Total` fields

```
|--------------------| Dwellings, Location on census night |
| SA2 | NPRD | HCFMD |                                     |
|-----|------|-------|-------------------------------------|
|     |      |       |                  x                  |
|     |      |       |                  x                  |
```   
  * Distribution of households by number of bedrooms in dwelling (BEDD) by dwelling structure (STRD) by tenure and landlord type (TENLLD) by number of persons usually resident in dwelling (NPRD) by SA1. 

    Construct below table using "Counting Families, Place of Usual Residence" database and save as `buildpopulation/data/latch/raw/SA1, BEDD, STRD, TENLLD Tenure and Landlord Type and NPRD.zip` No `Total` fields in this table either.
```
|-----------------------------------| Dwellings, Location on census night |
| SA1 | BEDD | STRD | TENLLD | NPRD |                                     |
|-----|------|------|--------|------|-------------------------------------|
|     |      |      |        |      |                   x                 |
|     |      |      |        |      |                   x                 |
```   
  Following custom categories are used,
  
       Tenure Type (TENLLD)
       Owner - Owned outright, Owned with a mortgage
       Private renter - Real estate agent, Rented: Person not in same household, Rented: Other landlord type, Rented: Landlord type not stated
       Social renter - Rented: State or territory housing authority, Rented: Housing co-operative, community or church group 
       Other - Other tenure type, Tenure type not stated, Tenure type not applicable
       
       Dwelling Structure (STRD)
       Detached house - Separate house
       Townhouse or semi-detached - Semi-detached, row or terrace house, townhouse etc with one storey; Semi-detached, row or terrace house, townhouse etc with two or more storeys; House or flat attached to a shop, office, etc.; and Flat, unit or apartment attached to a house
       Flats or units (3 storeys or less) - Flat, unit or apartment in a one or two storey block; Flat, unit or apartment in a three storey block
       High-rise appartments (4+ storeys) - Flat, unit or apartment in a four or more storey block
       Other - Caravan, cabin, houseboat; Improvised home, tent, sleepers out; Not stated; and Not applicable
       
   * Household composition distributions of each SA2 by SA1s in it. 

     Construct below table in TableBuilder using "Counting Families, Place of Usual Residence" database for each SA2 and save the files in `buildpopulation/data/latch/raw/Hh-SA1-in-each-SA2/` directory. Remove `Total` fields from the table before saving. Both zip and csv are acceptable for this table.

```
|------| SA1s  | SA1_CODE1 | SA1_CODE2 | SA1_CODE3 |  ...  |
| NPRD | HCFMD |           |           |           |  ...  |
|------|-------|-----------|-----------|-----------|-------|
|      |       |     x     |     x     |     x     | ..x.. |
|      |       |     x     |     x     |     x     | ..x.. |
```   

  * Age distribution of persons in SA2s. 

    Construct the below table using "Counting Persons, Place of Usual Residence" database and save as `buildpopulation/data/latch/raw/AGEP by SA3.csv`. Keep `Total` column shown in below structure and remove `Total` row from AGEP column.

```
| SA3s | Darebin - South | Darebin - North | Banyule | Total |
| AGEP |                 |                 |         |       |
|------|-----------------|-----------------|---------|-------|
|      |        x        |        x        |    x    |   x   |
|      |        x        |        x        |    x    |   x   |
```

### 2. Download required Australian Statistical Geography Standard (ASGS) data cubes

  Download following files from http://www.abs.gov.au/AUSSTATS/abs@.nsf/DetailsPage/1270.0.55.001July%202011 . Save them in `data/latch/raw/All-Aus-SA1/`. Below two files cover all the SA1s in Australia, so do not need to be downloaded again for constructing a synthetic population in a new area.
  
   * [Statistical Area Level 1 (SA1) ASGS Ed 2011 Digital Boundaries in ESRI Shapefile Format](http://www.abs.gov.au/ausstats/subscriber.nsf/log?openagent&1270055001_sa1_2011_aust_shape.zip&1270.0.55.001&Data%20Cubes&24A18E7B88E716BDCA257801000D0AF1&0&July%202011&23.12.2010&Latest)
   * [Statistical Area Level 1 (SA1) ASGS Edition 2011 in .csv Format](http://www.abs.gov.au/ausstats/subscriber.nsf/log?openagent&1270055001_sa1_2011_aust_csv.zip&1270.0.55.001&Data%20Cubes&5AD36D669F284E70CA257801000C69BE&0&July%202011&23.12.2010&Latest)

### 3. Download building address shape files from Vicmap Data website

   1. Building Addresses shape files can be downloaded by selecting [Address](https://services.land.vic.gov.au/landchannel/content/vicmapdata?productID=1) from Vicmap Products list.
   
   2. Default location for saving downloaded zipped shapefiles is `buildpopulation/data/raw/address-shapefiles/`

  This is used to construct the intial dwellings distribution in the area. Geographical coordinates are used to find the SA1 each building belongs to.

### 4. Construct the population

Execute shell script in `buildpopulation/run`

        > cd buildpopulation/run/
        > sh run.sh

#### Individual Commands

Above `run` folder contains a shell script that calls R scripts to preprcess ABS data, build maven project and execute jar files. Below are the steps to follow to build the population manually. Further information is available in README files in each project directory.
* `/buildpopulation/datapreprocessing/` - R scripts for preprocessing SA2 level population data csvs downloaded using ABS TableBuilder
* `/buildpopulation/populationbuilder/latchpop/` - Java project for constructing the synthetic population
* `/buildpopulation/populationbuilder/BuildingProperties/` - Java project for assiging dwelling properties to buildings in selecte area

1. To preprocess ABS data, change directory to `buildpopulation/datapreprocessing/` and run below command,

        > ./sa2preprocess.R

      This will preprocess ABS data and save the processed files to `buildpopulation/data/latch/absprocessed/SA2/`

2. To construct the synthetic population, chagne directory to `buildpopulation/populationbuilder/` and run following commands;

      Build the project,
      
        > mvn clean install

      Above command will produce a jar file in `buildpopulation/populationbuilder/latchpop/`. Construct the synthetic population by running followng command
   
        > cd buildpopulation/populationbuilder/latchpop/
        > java -jar target/latchpop.jar population.properties
   
      This will save the constructed population files to `buildpopulation/data/latch/locationaldata`

3. Assign dwelling properties to buildinds based on ABS data by running following commands

        > cd buildpopulation/populationbuilder/BuildingProperties
        > java -jar target/buildingproperties.jar Buildings.properties

  
