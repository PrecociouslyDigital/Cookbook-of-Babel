#!/usr/bin/env bash
# Downloads the pinned OpenFoodFacts product database and categories taxonomy
# into .cache/openfoodfacts/, checks each against the SHA-256 recorded here,
# projects the products to products.tsv, and prints the folder. The folder
# also gets dataset-url, the pinned address the extracted source cites.
# The pins are commits, so every extraction is reproducible; bump them
# deliberately.
set -euo pipefail

dataset=openfoodfacts/product-database
dataset_rev=24a6d86e83437a177f6862aaafcfba12c8c7664a
dataset_sha256=3a0d815f240b2c023424eae60ebc93e3d1fe9430b9ac21b0278a00ff2049b7ad
server_rev=54ea7c917eca14077b907ac7ad0cbbd63b5e3d02
taxonomy_sha256=a3237d67cdf4dcbb26f9e21d29d218bd2d6da40fc2bc4f5b5088f39116a20e1b

root=$(git rev-parse --show-toplevel)
cache=$root/.cache/openfoodfacts
mkdir -p "$cache"

# fetch <url> <file> <sha256>
fetch() {
	local url=$1 file=$cache/$2 sha256=$3
	if [[ -f $file.verified ]]; then return; fi
	curl -fsSL --retry 3 -C - -o "$file" "$url"
	if [[ $(shasum -a 256 "$file" | cut -d' ' -f1) != "$sha256" ]]; then
		rm -f "$file"
		echo "$2: checksum mismatch; deleted it, rerun to download again" >&2
		exit 1
	fi
	touch "$file.verified"
}

fetch "https://huggingface.co/datasets/$dataset/resolve/$dataset_rev/food.parquet" food.parquet "$dataset_sha256"
fetch "https://raw.githubusercontent.com/openfoodfacts/openfoodfacts-server/$server_rev/taxonomies/food/categories.txt" categories.txt "$taxonomy_sha256"
echo "https://huggingface.co/datasets/$dataset/blob/$dataset_rev/food.parquet" >"$cache/dataset-url"

# One row per categorised product. Tags are joined by commas, which no tag
# contains; tabs and line breaks in free text become spaces.
if [[ ! -f $cache/products.tsv || $cache/food.parquet.verified -nt $cache/products.tsv ]]; then
	duckdb -c "
		CREATE MACRO per100g(ns, wanted) AS [n.\"100g\" FOR n IN ns IF n.name = wanted][1];
		CREATE MACRO oneline(t) AS regexp_replace(t, '[\t\r\n]', ' ', 'g');
		COPY (
			SELECT
				oneline(code) AS code,
				array_to_string(categories_tags, ',') AS categories_tags,
				per100g(nutriments, 'energy-kcal') AS kcal,
				per100g(nutriments, 'energy-kj') AS kj,
				per100g(nutriments, 'proteins') AS protein,
				per100g(nutriments, 'fat') AS fat,
				per100g(nutriments, 'carbohydrates') AS carbs,
				oneline(serving_size) AS serving_size,
				oneline(serving_quantity) AS serving_quantity
			FROM read_parquet('$cache/food.parquet')
			WHERE len(categories_tags) > 0
		) TO '$cache/products.tsv.partial' (DELIMITER E'\t', HEADER, QUOTE '', ESCAPE '');
	"
	mv "$cache/products.tsv.partial" "$cache/products.tsv"
fi
echo "$cache"
