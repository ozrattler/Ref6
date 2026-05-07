package migrations

import (
	"github.com/pocketbase/pocketbase/core"
	m "github.com/pocketbase/pocketbase/migrations"
)

func init() {
	m.Register(func(app core.App) error {
		matches := core.NewBaseCollection("matches")
		matches.Fields.Add(
			&core.DateField{Name: "date", Required: true},
			&core.TextField{Name: "competition"},
			&core.TextField{Name: "home_team", Required: true},
			&core.TextField{Name: "away_team", Required: true},
			&core.TextField{Name: "final_score"},
			&core.TextField{Name: "age_group"},
			&core.NumberField{Name: "half_length"},
			&core.TextField{Name: "status"},
		)
		if err := app.Save(matches); err != nil {
			return err
		}

		incidents := core.NewBaseCollection("incidents")
		incidents.Fields.Add(
			&core.RelationField{
				Name:         "match_id",
				CollectionId: matches.Id,
				Required:     true,
			},
			&core.NumberField{Name: "minute"},
			&core.TextField{Name: "type"},
			&core.TextField{Name: "team"},
			&core.TextField{Name: "player_number"},
			&core.TextField{Name: "player_name"},
			&core.TextField{Name: "offence_description"},
		)
		return app.Save(incidents)
	}, func(app core.App) error {
		for _, name := range []string{"incidents", "matches"} {
			col, err := app.FindCollectionByNameOrId(name)
			if err == nil {
				_ = app.Delete(col)
			}
		}
		return nil
	})
}
